package org.qweyns.qweprotectstones.features.importer;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Импорт приватов из других плагинов: WorldGuard, ProtectionStones (его
 * регионы хранятся в WorldGuard), GriefPrevention.
 *
 * <p>Чужие файлы только читаются — ни один плагин не должен быть установлен.
 * Импортированным приватам назначается тип из {@code import.type-id}; блок-ядро
 * виртуальное: границы не обязаны быть симметричны вокруг него, поэтому
 * перенесённые территории сохраняют точные размеры.</p>
 */
public class RegionImporter {

    /** Итог одного прогона импорта. */
    public record Result(int imported, int skipped, int errors) {
    }

    private final QweProtectStones plugin;

    public RegionImporter(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // WorldGuard и ProtectionStones
    // ------------------------------------------------------------------

    /**
     * Импорт из regions.yml WorldGuard. ProtectionStones создаёт обычные
     * WG-регионы с id, начинающимся на {@code ps} — флаг {@code stonesOnly}
     * отбирает только их.
     */
    public Result importFromWorldGuard(boolean stonesOnly) {
        File folder = new File(dataFolder(stonesOnly
                ? "import.protectionstones.data-folder"
                : "import.worldguard.data-folder",
                stonesOnly ? "plugins/WorldGuard" : "plugins/WorldGuard"));

        File worldsDir = new File(folder, "worlds");
        File[] worldFolders = worldsDir.listFiles(File::isDirectory);
        if (worldFolders == null || worldFolders.length == 0) {
            plugin.getLogger().warning("Импорт: папка не найдена или пуста — " + worldsDir.getPath());
            return new Result(0, 0, 1);
        }

        int imported = 0, skipped = 0, errors = 0;
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
                    if (importCuboid(worldName, data)) imported++;
                    else skipped++;
                }
            } catch (Exception e) {
                errors++;
                plugin.getLogger().log(Level.WARNING, "Импорт: не удалось прочитать " + regionsFile.getPath(), e);
            }
        }
        return new Result(imported, skipped, errors);
    }

    /** Один cuboid-регион WG/PS → наш приват. */
    private boolean importCuboid(String worldName, Map<?, ?> data) {
        Object minRaw = data.get("min");
        Object maxRaw = data.get("max");
        if (!(minRaw instanceof Map<?, ?> min) || !(maxRaw instanceof Map<?, ?> max)) return false;

        World world = Bukkit.getWorld(worldName);
        int minY = intOf(min.get("y"));
        int maxY = intOf(max.get("y"));
        if (world != null) {
            minY = Math.max(world.getMinHeight(), minY);
            maxY = Math.min(world.getMaxHeight() - 1, maxY);
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
        return plugin.getRegionManager().importRegion(region);
    }

    // ------------------------------------------------------------------
    // GriefPrevention
    // ------------------------------------------------------------------

    /**
     * Импорт из ClaimData GriefPrevention: каждый клейм — отдельный yml-файл.
     * Субклеймы (Parent Claim ID ≥ 0) пропускаются: они внутри родительских.
     */
    public Result importFromGriefPrevention() {
        File folder = new File(dataFolder("import.griefprevention.data-folder", "plugins/GriefPreventionData"));
        File claimsDir = new File(folder, "ClaimData");
        File[] files = claimsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("Импорт: папка не найдена или пуста — " + claimsDir.getPath());
            return new Result(0, 0, 1);
        }

        int imported = 0, skipped = 0, errors = 0;
        for (File file : files) {
            try (InputStream in = new FileInputStream(file)) {
                Object raw = new Yaml().load(in);
                if (!(raw instanceof Map<?, ?> data)) continue;

                // Субклейм без собственного владельца не импортируем.
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
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    skipped++;
                    continue;
                }

                // Клеймы GP — всегда колонна от бедрока до неба.
                RegionBounds bounds = new RegionBounds(min[0], world.getMinHeight(), min[2],
                        max[0], world.getMaxHeight() - 1, max[2]);

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

                if (plugin.getRegionManager().importRegion(region)) imported++;
                else skipped++;
            } catch (IOException e) {
                errors++;
                plugin.getLogger().log(Level.WARNING, "Импорт: не удалось прочитать " + file.getPath(), e);
            }
        }
        return new Result(imported, skipped, errors);
    }

    private void importGpList(Region region, Object raw, TrustLevel level, UUID ownerId) {
        if (!(raw instanceof List<?> list)) return;
        for (Object entry : list) {
            UUID uuid = parseUuid(String.valueOf(entry));
            if (uuid == null || uuid.equals(ownerId)) continue;
            region.setMember(uuid, nameOf(uuid), level);
        }
    }

    // ------------------------------------------------------------------
    // Общее
    // ------------------------------------------------------------------

    /** Собрать приват с виртуальным ядром в центре границ. */
    private Region buildRegion(String worldName, RegionBounds bounds, UUID ownerId, String ownerName) {
        return buildRegion(worldName, bounds, ownerId, ownerName, null);
    }

    /**
     * @param typeIdOverride тип привата, вычисленный при импорте (например,
     *                       по материалу блока ProtectionStones); {@code null} —
     *                       брать общий {@code import.type-id}
     */
    private Region buildRegion(String worldName, RegionBounds bounds, UUID ownerId, String ownerName, String typeIdOverride) {
        if (ownerId == null) return null; // административные регионы не переносим

        RegionType type = null;
        if (typeIdOverride != null) {
            type = plugin.getRegionTypes().byId(typeIdOverride);
        }
        if (type == null) type = plugin.getRegionTypes().resolveOrFallback(importTypeId());
        if (type == null) return null;

        int coreX = bounds.centerX();
        int coreZ = bounds.centerZ();
        // Виртуальное ядро — в центре объёма: оно нигде не ставится физически.
        int coreY = (bounds.minY() + bounds.maxY()) / 2;

        Region region = new Region(UUID.randomUUID(), worldName, bounds,
                coreX, coreY, coreZ, type.id(), ownerId, ownerName,
                type.startDurability(), type.maxDurability(), System.currentTimeMillis());

        if (plugin.getConfigManager().getConfig().getBoolean("import.create-holograms", false)) {
            plugin.getHologramManager().createOrUpdateHologram(region);
        }
        return region;
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

    // ------------------------------------------------------------------
    // ProtectionStones: тип блока и флаги
    // ------------------------------------------------------------------

    /**
     * ProtectionStones помечает свои регионы WorldGuard флагом
     * {@code ps-block-material} — по нему подбираем тип один в один
     * (ключи типов в regions.yml совпадают с материалами). Настраивается
     * {@code import.worldguard.type-by-material}.
     */
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

    /**
     * Перенос логических флагов WorldGuard в наши. Соответствие имён —
     * {@code import.worldguard.flag-mapping} в config.yml, значения по
     * умолчанию — {@link WgFlags#DEFAULT_MAPPING}. Неизвестные и
     * не-логические флаги пропускаются.
     */
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

    // ------------------------------------------------------------------
    // Парсинг
    // ------------------------------------------------------------------

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

    /** «world;x;y;z» → [x, y, z]. */
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

    /** Мир из «world;x;y;z». */
    private static String worldOf(String raw) {
        int cut = raw.indexOf(';');
        return cut > 0 ? raw.substring(0, cut) : raw;
    }

    private String nameOf(UUID uuid) {
        if (uuid == null) return "";
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() != null ? player.getName() : uuid.toString().substring(0, 8);
    }

    /** Обновить карты после импорта. */
    public void refreshMaps() {
        if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().redrawAll();
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().updateAll();
    }
}
