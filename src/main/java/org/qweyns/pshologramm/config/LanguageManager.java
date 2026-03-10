package org.qweyns.pshologramm.config;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.utils.ColorUtil;

import java.io.File;

public class LanguageManager {
    private final PSHologramm plugin;
    private FileConfiguration langConfig;

    public LanguageManager(PSHologramm plugin) {
        this.plugin = plugin;
    }

    public void init() {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        saveDefaultLang("ru_RU.yml");
        saveDefaultLang("en_US.yml");
        saveDefaultLang("es_ES.yml");
        saveDefaultLang("zh_CN.yml");

        String selectedLang = plugin.getConfigManager().getConfig().getString("settings.language", "ru_RU");
        File langFile = new File(langFolder, selectedLang + ".yml");

        if (!langFile.exists()) {
            plugin.getLogger().warning("Файл языка " + selectedLang + ".yml не найден! Загружаем en_US.yml по умолчанию.");
            langFile = new File(langFolder, "en_US.yml");
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    private void saveDefaultLang(String fileName) {
        File file = new File(plugin.getDataFolder(), "lang/" + fileName);
        if (!file.exists()) {
            try {
                plugin.saveResource("lang/" + fileName, false);
            } catch (Exception e) {
            }
        }
    }

    public Component getMessage(String path, String... replacements) {
        String prefix = langConfig.getString("prefix", "");
        String msg = langConfig.getString(path, "");
        if (msg == null || msg.isEmpty()) return Component.empty();

        String full = msg.replace("%prefix%", prefix);
        for (int i = 0; i < replacements.length; i += 2) {
            full = full.replace(replacements[i], replacements[i + 1]);
        }
        return ColorUtil.formatComponent(full);
    }

    public String getRawMessage(String path, String... replacements) {
        String prefix = langConfig.getString("prefix", "");
        String msg = langConfig.getString(path, "");
        if (msg == null) return "";

        String full = msg.replace("%prefix%", prefix);
        for (int i = 0; i < replacements.length; i += 2) {
            full = full.replace(replacements[i], replacements[i + 1]);
        }
        return ColorUtil.formatLegacyString(full);
    }
}
