package org.qweyns.qweprotectstones.features.importer;

import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.regions.RegionMember;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.TrustLevel;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RegionRestorer {

    public record Result(int restored, int skipped, int errors) {
    }

    private final QweProtectStones plugin;

    public RegionRestorer(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public Result restore(File file) throws IOException {
        String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);

        Object parsed;
        try {
            parsed = new Yaml().load(raw);
        } catch (RuntimeException e) {
            throw new IOException("файл не похож на выгрузку QweProtectStones: " + e.getMessage(), e);
        }
        if (!(parsed instanceof Map<?, ?> root) || !(root.get("regions") instanceof List<?> entries)) {
            throw new IOException("в файле нет секции regions");
        }

        int restored = 0, skipped = 0, errors = 0;
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> data)) continue;

            try {
                switch (restoreOne(data)) {
                    case RESTORED -> restored++;
                    case SKIPPED -> skipped++;
                    case ERROR -> errors++;
                }
            } catch (RuntimeException e) {
                errors++;
                plugin.getLogger().warning("Restore: запись пропущена из-за ошибки — " + e.getMessage());
            }
        }
        return new Result(restored, skipped, errors);
    }

    private enum Outcome {RESTORED, SKIPPED, ERROR}

    private Outcome restoreOne(Map<?, ?> data) {
        UUID id = uuid(String.valueOf(data.get("id")));
        RegionType type = plugin.getRegionTypes().byId(String.valueOf(data.get("type")).toLowerCase(java.util.Locale.ROOT));
        if (id == null || type == null) return Outcome.ERROR;

        if (plugin.getRegionManager().getById(id) != null) return Outcome.SKIPPED;

        String world = String.valueOf(data.get("world"));
        UUID ownerId = data.get("owner") instanceof Map<?, ?> owner ? uuid(String.valueOf(owner.get("uuid"))) : null;
        if (ownerId == null) return Outcome.SKIPPED;

        int[] core = intArray(data.get("core"));
        RegionBounds bounds = bounds(data.get("bounds"));
        if (core == null || bounds == null) return Outcome.ERROR;

        int durability = intOf(data.get("durability"), type.startDurability());
        int maxDurability = Math.max(1, intOf(data.get("max_durability"), type.maxDurability()));
        long createdAt = longOf(data.get("created_at"), System.currentTimeMillis());

        Region region = new Region(id, world, bounds,
                core[0], core[1], core[2], type.id(),
                ownerId, ownerName(data), durability, maxDurability, createdAt);

        region.restoreDecoration(
                string(data.get("name")),
                string(data.get("greeting")),
                string(data.get("farewell")));

        if (data.get("attacks") instanceof Map<?, ?> attacks) {
            region.restoreStats(
                    intOf(attacks.get("count"), 0),
                    longOf(attacks.get("last_at"), 0L),
                    string(attacks.get("last_by")));
        }

        if (data.get("members") instanceof List<?> members) {
            for (Object memberEntry : members) {
                if (!(memberEntry instanceof Map<?, ?> member)) continue;
                UUID memberId = uuid(String.valueOf(member.get("uuid")));
                var trust = TrustLevel.parse(String.valueOf(member.get("trust")));
                if (memberId == null || trust.isEmpty()) continue;
                region.restoreMember(new RegionMember(memberId,
                        string(member.get("name")), trust.get(), System.currentTimeMillis()));
            }
        }

        if (data.get("bans") instanceof List<?> bans) {
            for (Object banEntry : bans) {
                if (!(banEntry instanceof Map<?, ?> ban)) continue;
                UUID banId = uuid(String.valueOf(ban.get("uuid")));
                if (banId != null) region.restoreBan(banId, string(ban.get("name")));
            }
        }

        if (data.get("flags") instanceof Map<?, ?> flags) {
            for (Map.Entry<?, ?> flag : flags.entrySet()) {
                var parsed = RegionFlag.parse(String.valueOf(flag.getKey()));
                if (parsed.isPresent() && flag.getValue() instanceof Boolean value) {
                    region.setFlag(parsed.get(), value);
                }
            }
        }

        if (data.get("effects") instanceof List<?> effects) {
            List<String> names = new ArrayList<>();
            for (Object effect : effects) names.add(String.valueOf(effect));
            region.getEffects().addAll(names);
        }

        if (!plugin.getRegionManager().importRegion(region)) return Outcome.SKIPPED;

        if (plugin.getConfigManager().getConfig().getBoolean("import.create-holograms", false)) {
            plugin.getHologramManager().createOrUpdateHologram(region);
        }
        return Outcome.RESTORED;
    }

    private RegionBounds bounds(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return null;
        int[] min = intArray(map.get("min"));
        int[] max = intArray(map.get("max"));
        if (min == null || max == null) return null;
        return new RegionBounds(min[0], min[1], min[2], max[0], max[1], max[2]);
    }

    private int[] intArray(Object raw) {
        if (!(raw instanceof List<?> list) || list.size() < 3) return null;
        try {
            return new int[]{
                    ((Number) list.get(0)).intValue(),
                    ((Number) list.get(1)).intValue(),
                    ((Number) list.get(2)).intValue()};
        } catch (ClassCastException | NullPointerException e) {
            return null;
        }
    }

    private String ownerName(Map<?, ?> data) {
        if (data.get("owner") instanceof Map<?, ?> owner) {
            String name = string(owner.get("name"));
            if (!name.isBlank()) return name;
        }
        return "Unknown";
    }

    private static String string(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }

    private static int intOf(Object raw, int fallback) {
        return raw instanceof Number number ? number.intValue() : fallback;
    }

    private static long longOf(Object raw, long fallback) {
        return raw instanceof Number number ? number.longValue() : fallback;
    }

    private static UUID uuid(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
