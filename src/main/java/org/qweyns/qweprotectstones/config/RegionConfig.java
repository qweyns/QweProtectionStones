package org.qweyns.qweprotectstones.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.qweyns.qweprotectstones.QweProtectStones;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

public class RegionConfig {

    private static final String FILE_NAME = "regions.yml";
    private static final String DEFAULTS_SECTION = "default_region";
    private static final String TYPES_SECTION = "region_types";

    private final QweProtectStones plugin;
    private final File file;

    private FileConfiguration config;

    public RegionConfig(QweProtectStones plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
        reload();
    }

    public void reload() {
        if (!file.isFile()) createInitialFile();

        config = YamlConfiguration.loadConfiguration(file);

        if (types() == null) {
            plugin.getLogger().severe(FILE_NAME + ": нет секции " + TYPES_SECTION
                    + " — приваты создавать будет нечем!");
        }
    }

    private static final String LEGACY_DEFAULTS_SECTION = "default_claim";
    private static final String LEGACY_TYPES_SECTION = "claim_types";

    private static final String LEGACY_FILE_NAME = "claims.yml";

    private void createInitialFile() {
        File legacyFile = new File(plugin.getDataFolder(), LEGACY_FILE_NAME);
        if (legacyFile.isFile()) {
            FileConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
            if (migrate(legacy, "из " + LEGACY_FILE_NAME)) {
                // старый файл не трогаем, это чужие настройки

                plugin.getLogger().info(LEGACY_FILE_NAME + " больше не читается — можно удалить вручную.");
                return;
            }
        }

        if (migrate(plugin.getConfig(), "из config.yml")) return;

        plugin.saveResource(FILE_NAME, false);
    }

    private boolean migrate(FileConfiguration source, String origin) {
        Object types = source.get(TYPES_SECTION);
        Object defaults = source.get(DEFAULTS_SECTION);

        if (types == null) {
            types = source.get(LEGACY_TYPES_SECTION);
            defaults = source.get(LEGACY_DEFAULTS_SECTION);
        }
        if (types == null) return false;

        YamlConfiguration migrated = new YamlConfiguration();
        migrated.options().setHeader(List.of(
                "Настройки приватов QweProtectStones.",
                "Файл создан автоматически: настройки типов перенесены " + origin + ".",
                "Секции переименованы в default_region и region_types."));

        migrated.set(DEFAULTS_SECTION, defaults);
        migrated.set(TYPES_SECTION, types);

        try {
            migrated.save(file);
            plugin.getLogger().info("Настройки приватов перенесены " + origin + " в " + FILE_NAME + ".");
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось создать " + FILE_NAME, e);
            return false;
        }
    }

    public FileConfiguration raw() {
        return config;
    }

    public ConfigurationSection defaults() {
        return config.getConfigurationSection(DEFAULTS_SECTION);
    }

    public ConfigurationSection types() {
        return config.getConfigurationSection(TYPES_SECTION);
    }

    public String path(String typeId, String key) {
        String specific = TYPES_SECTION + "." + typeId + "." + key;
        return config.contains(specific) ? specific : DEFAULTS_SECTION + "." + key;
    }

    public String sectionPath(String typeId, String key) {
        String specific = TYPES_SECTION + "." + typeId + "." + key;
        if (config.contains(specific)) return specific;

        String fallback = DEFAULTS_SECTION + "." + key;
        return config.contains(fallback) ? fallback : null;
    }

    public String getString(String typeId, String key, String fallback) {
        return config.getString(path(typeId, key), fallback);
    }

    public int getInt(String typeId, String key, int fallback) {
        return config.getInt(path(typeId, key), fallback);
    }

    public double getDouble(String typeId, String key, double fallback) {
        return config.getDouble(path(typeId, key), fallback);
    }

    public boolean getBoolean(String typeId, String key, boolean fallback) {
        return config.getBoolean(path(typeId, key), fallback);
    }

    public List<String> getStringList(String typeId, String key) {
        return config.getStringList(path(typeId, key));
    }

    public boolean has(String typeId, String key) {
        return config.contains(TYPES_SECTION + "." + typeId + "." + key)
                || config.contains(DEFAULTS_SECTION + "." + key);
    }
}
