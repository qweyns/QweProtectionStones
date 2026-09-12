package org.qweyns.qweprotectstones.features.importer;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.TrustLevel;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class RegionImporter {

    /** Разобранные, но ещё не зарегистрированные приваты: регистрацию делаем в потоке сервера. */
    public record Pending(List<Region> regions, int skipped, int errors) {
    }

    private final QweProtectStones plugin;

    public RegionImporter(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    /** Читает и разбирает выгрузку WorldGuard без регистрации — можно звать вне основного потока. */
    public Pending parseWorldGuard(boolean stonesOnly, Map<String, int[]> worldBounds) {
        File folder = new File(dataFolder(stonesOnly
                ? "import.protectionstones.data-folder"
                : "import.worldguard.data-folder",
                stonesOnly ? "plugins/WorldGuard" : "plugins/WorldGuard"));

        List<Region> parsed = new ArrayList<>();
        File worldsDir = new File(folder, "worlds");
        File[] worldFolders = worldsDir.listFiles(File::isDirectory);
        if (worldFolders == null || worldFolders.length == 0) {
            plugin.getLogger().warning("Импорт: папка не найдена или пуста — " + worldsDir.getPath());
            return new Pending(List.of(), 0, 1);
        }

        int skipped = 0, errors = 0;
        for (File worldFolder : worldFolders) {
            File regionsFile = new File(worldFolder, "regions.yml");
            if (!regionsFile.isFile()) continue;

            String worldName = worldFolder.getName();
            try (InputStream in = new FileInputStream(regionsFile)) {
                Object raw = new Yaml().load(in);
                if (!(raw instanceof Map<?, ?> root)) continue;
                Object regionsRaw = root.get("regions");
                if (!(regionsRaw instanceof Map<?, ?> regions)) continue;

                for (Map.Entry<?, ?> entry : regions.entrySet()) {
                    String id = String.valueOf(entry.getKey());
                    if (id.equals("__global__")) continue;
                    if (stonesOnly != id.toLowerCase().startsWith("ps")) continue;
                    if (!(entry.getValue() instanceof Map<?, ?> data)) continue;

                    String type = String.valueOf(data.get("type"));
                    if (!type.equals("cuboid")) {
                        skipped++;
                        continue;
                    }
                    Region region = parseCuboid(worldName, data, worldBounds);
                    if (region != null) parsed.add(region);
                    else skipped++;
                }
            } catch (Exception e) {
                errors++;
                plugin.getLogger().log(Level.WARNING, "Импорт: не удалось прочитать " + regionsFile.getPath(), e);
            }
        }
        return new Pending(parsed, skipped, errors);
    }

    private Region parseCuboid(String worldName, Map<?, ?> data, Map<String, int[]> worldBounds) {
        Object minRaw = data.get("min");
        Object maxRaw = data.get("max");
        if (!(minRaw instanceof Map<?, ?> min) || !(maxRaw instanceof Map<?, ?> max)) return null;

        int minY = intOf(min.get("y"));
        int maxY = intOf(max.get("y"));
        int[] limits = worldBounds.get(worldName);
        if (limits != null) {
            minY = Math.max(limits[0], minY);
            maxY = Math.min(limits[1], maxY);
        }

        RegionBounds bounds = new RegionBounds(
                intOf(min.get("x")), minY, intOf(min.get("z")),
                intOf(max.get("x")), maxY, intOf(max.get("z")));

        UUID ownerId = null;
        String ownerName = null;
        if (data.get("owners") instanceof Map<?, ?> owners && owners.get("players") instanceof Map<?, ?> players) {
            for (Map.Entry<?, ?> owner : players.entrySet()) {
                UUID uuid = parseUuid(owner.getKey());
                if (uuid != null) {
                    ownerId = uuid;
                    ownerName = String.valueOf(owner.getValue());
                    break;
                }
            }
        }

        Region region = buildRegion(worldName, bounds, ownerId, ownerName, psMaterial(data));
        if (region == null) return false;

        applyWgFlags(region, data);

        if (data.get("members") instanceof Map<?, ?> members && members.get("players") instanceof Map<?, ?> players) {
            for (Map.Entry<?, ?> member : players.entrySet()) {
                UUID uuid = parseUuid(member.getKey());
                if (uuid == null || uuid.equals(ownerId)) continue;
                region.setMember(uuid, String.valueOf(member.getValue()), TrustLevel.BUILD);
            }
        }
        return region;
    }

    /** Читает и разбирает данные GriefPrevention без регистрации — можно звать вне основного потока. */
    public Pending parseGriefPrevention(Map<String, int[]> worldBounds) {
        File folder = new File(dataFolder("import.griefprevention.data-folder", "plugins/GriefPreventionData"));
        File claimsDir = new File(folder, "ClaimData");
        File[] files = claimsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("Импорт: папка не найдена или пуста — " + claimsDir.getPath());
            return new Pending(List.of(), 0, 1);
        }

        List<Region> parsed = new ArrayList<>();
        int skipped = 0, errors = 0;
        for (File file : files) {
            try (InputStream in = new FileInputStream(file)) {
                Object raw = new Yaml().load(in);
                if (!(raw instanceof Map<?, ?> data)) continue;

                if (data.get("Parent Claim ID") instanceof Number parent && parent.longValue() >= 0) {
                    skipped++;
                    continue;
                }

                Object lesser = data.get("Lesser Boundary Corner");
                Object greater = data.get("Greater Boundary Corner");
                if (!(lesser instanceof String lesserStr) || !(greater instanceof String greaterStr)) continue;

                int[] min = parseGpLocation(lesserStr);
                int[] max = parseGpLocation(greaterStr);
                if (min == null || max == null) continue;

                String worldName = worldOf(lesserStr);
                int[] limits = worldBounds.get(worldName);
                if (limits == null) {
                    skipped++;
                    continue;
                }

                RegionBounds bounds = new RegionBounds(min[0], limits[0], min[2],
                        max[0], limits[1], max[2]);

                UUID ownerId = parseUuid(String.valueOf(data.get("Owner")));
                String ownerName = nameOf(ownerId);
                Region region = buildRegion(worldName, bounds, ownerId, ownerName);
                if (region == null) {
                    skipped++;
                    continue;
                }

                importGpList(region, data.get("Builders"), TrustLevel.BUILD, ownerId);
                importGpList(region, data.get("Containers"), TrustLevel.CONTAINER, ownerId);
                importGpList(region, data.get("Accessors"), TrustLevel.ACCESS, ownerId);
                importGpList(region, data.get("Managers"), TrustLevel.MANAGER, ownerId);

                parsed.add(region);
            } catch (IOException e) {
                errors++;
                plugin.getLogger().log(Level.WARNING, "Импорт: не удалось прочитать " + file.getPath(), e);
            }
        }
        return new Pending(parsed, skipped, errors);
    }

    private void importGpList(Region region, Object raw, TrustLevel level, UUID ownerId) {
        if (!(raw instanceof List<?> list)) return;
        for (Object entry : list) {
            UUID uuid = parseUuid(String.valueOf(entry));
            if (uuid == null || uuid.equals(ownerId)) continue;
            region.setMember(uuid, nameOf(uuid), level);
        }
    }

    private Region buildRegion(String worldName, RegionBounds bounds, UUID ownerId, String ownerName) {
        return buildRegion(worldName, bounds, ownerId, ownerName, null);
    }

    private Region buildRegion(String worldName, RegionBounds bounds, UUID ownerId, String ownerName, String typeIdOverride) {
        if (ownerId == null) return null;

        RegionType type = null;
        if (typeIdOverride != null) {
            type = plugin.getRegionTypes().byId(typeIdOverride);
        }
        if (type == null) type = plugin.getRegionTypes().resolveOrFallback(importTypeId());
        if (type == null) return null;

        int coreX = bounds.centerX();
        int coreZ = bounds.centerZ();
        // ядро виртуальное, в центре объёма
        int coreY = (bounds.minY() + bounds.maxY()) / 2;

        return new Region(UUID.randomUUID(), worldName, bounds,
                coreX, coreY, coreZ, type.id(), ownerId, ownerName,
                type.startDurability(), type.maxDurability(), System.currentTimeMillis());
    }

    private String importTypeId() {
        String raw = plugin.getConfigManager().getConfig().getString("import.type-id", "");
        return raw == null ? "" : raw.trim();
    }

    private String dataFolder(String path, String fallback) {
        String raw = plugin.getConfigManager().getConfig().getString(path, fallback);
        if (raw == null || raw.isBlank()) return fallback;
        return raw;
    }

    private String psMaterial(Map<?, ?> data) {
        if (!(data.get("flags") instanceof Map<?, ?> flags)) return null;
        Object material = flags.get("ps-block-material");
        if (material == null) return null;

        String raw = String.valueOf(material).trim();
        if (raw.isEmpty()) return null;

        if (!plugin.getConfigManager().getConfig().getBoolean("import.worldguard.type-by-material", true)) {
            return null;
        }
        return plugin.getRegionTypes().byId(raw) != null ? raw : null;
    }

    private void applyWgFlags(Region region, Map<?, ?> data) {
        if (!(data.get("flags") instanceof Map<?, ?> flags)) return;

        org.bukkit.configuration.ConfigurationSection section =
                plugin.getConfigManager().getConfig().getConfigurationSection("import.worldguard.flag-mapping");
        java.util.Map<String, String> mapping;
        if (section != null) {
            java.util.Map<String, String> raw = new java.util.HashMap<>();
            for (String key : section.getKeys(false)) {
                raw.put(key, section.getString(key));
            }
            mapping = WgFlags.normalize(raw);
        } else {
            mapping = WgFlags.DEFAULT_MAPPING;
        }

        for (Map.Entry<?, ?> flag : flags.entrySet()) {
            String our = mapping.get(String.valueOf(flag.getKey()).trim().toLowerCase(java.util.Locale.ROOT));
            if (our == null) continue;

            org.qweyns.qweprotectstones.regions.RegionFlag target = WgFlags.resolve(our);
            if (target == null) continue;

            Boolean value = WgFlags.mapValue(String.valueOf(flag.getValue()));
            if (value == null) continue;

            region.setFlag(target, value);
        }
    }

    private static int intOf(Object raw) {
        if (raw instanceof Number number) return number.intValue();
        try {
            return (int) Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static UUID parseUuid(Object raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private int[] parseGpLocation(String raw) {
        String[] parts = raw == null ? new String[0] : raw.split(";");
        if (parts.length < 4) return null;
        try {
            return new int[]{
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()),
                    Integer.parseInt(parts[3].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String worldOf(String raw) {
        int cut = raw.indexOf(';');
        return cut > 0 ? raw.substring(0, cut) : raw;
    }

    private String nameOf(UUID uuid) {
        if (uuid == null) return "";
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() != null ? player.getName() : uuid.toString().substring(0, 8);
    }

    public void refreshMaps() {
        if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().redrawAll();
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().updateAll();
    }
}
