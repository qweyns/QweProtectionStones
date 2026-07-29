package org.qweyns.qweprotectstones.config;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.utils.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public class LanguageManager {
    private static final List<String> BUNDLED_LANGUAGES = List.of("ru_RU", "en_US", "es_ES", "zh_CN");
    private static final String FALLBACK_LANGUAGE = "en_US";

    private final QweProtectStones plugin;
    private FileConfiguration langConfig = new YamlConfiguration();

    public LanguageManager(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public void init() {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.isDirectory() && !langFolder.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку lang/ — будут использованы встроенные переводы.");
        }

        BUNDLED_LANGUAGES.forEach(this::saveDefaultLang);

        String selectedLang = plugin.getConfigManager().getConfig().getString("settings.language", "ru_RU");
        File langFile = new File(langFolder, selectedLang + ".yml");

        if (!langFile.isFile()) {
            plugin.getLogger().warning("Файл языка " + selectedLang + ".yml не найден! Загружаю " + FALLBACK_LANGUAGE + ".yml.");
            langFile = new File(langFolder, FALLBACK_LANGUAGE + ".yml");
        }

        FileConfiguration loaded = langFile.isFile()
                ? YamlConfiguration.loadConfiguration(langFile)
                : new YamlConfiguration();

        // Ключи, которых нет в пользовательском файле, берутся из встроенного в jar —
        // иначе после обновления плагина новые сообщения приходили бы пустыми.
        loadBundled(selectedLang)
                .or(() -> loadBundled(FALLBACK_LANGUAGE))
                .ifPresent(loaded::setDefaults);

        this.langConfig = loaded;
    }

    private Optional<YamlConfiguration> loadBundled(String language) {
        try (InputStream in = plugin.getResource("lang/" + language + ".yml")) {
            if (in == null) return Optional.empty();
            return Optional.of(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void saveDefaultLang(String language) {
        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        if (file.isFile()) return;
        try {
            plugin.saveResource("lang/" + language + ".yml", false);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Встроенный языковой файл " + language + ".yml отсутствует в jar.", e);
        }
    }

    public Component getMessage(String path, String... replacements) {
        String raw = resolve(path, replacements);
        return raw.isEmpty() ? Component.empty() : ColorUtil.formatComponent(raw);
    }

    public String getRawMessage(String path, String... replacements) {
        return ColorUtil.formatLegacyString(resolve(path, replacements));
    }

    /**
     * Оформляет произвольный текст, а не ключ из lang-файла: так игрок может
     * задать своё приветствие с цветами и плейсхолдерами.
     */
    public Component format(String text, String... replacements) {
        if (text == null || text.isEmpty()) return Component.empty();
        return ColorUtil.formatComponent(applyReplacements(text, replacements));
    }

    private String resolve(String path, String... replacements) {
        String msg = langConfig.getString(path);
        if (msg == null || msg.isEmpty()) return "";

        return applyReplacements(msg.replace("%prefix%", langConfig.getString("prefix", "")), replacements);
    }

    /** Пары «плейсхолдер, значение»: непарный хвост игнорируем, чтобы не ловить ArrayIndexOutOfBounds. */
    private String applyReplacements(String text, String... replacements) {
        String full = text;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            if (replacements[i] == null) continue;
            full = full.replace(replacements[i], String.valueOf(replacements[i + 1]));
        }
        return full;
    }
}
