package org.qweyns.qweprotectstones.config;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.utils.ColorUtil;

import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    /**
     * Сообщение из lang-файла — работает с ЛЮБЫМ ключом, как бы тот ни был
     * записан: одиночной строкой или списком. Список превращается в
     * многострочный компонент (строки разделены переводом строки), поэтому
     * любой {@code sendMessage(getMessage(...))} в плагине выводит все строки.
     *
     * <p>МиниMessage-теги, включая кликабельность ({@code <click:...>},
     * {@code <hover:...>}), работают в каждой строке.</p>
     */
    public Component getMessage(String path, String... replacements) {
        if (langConfig.isList(path)) {
            List<Component> lines = getMessageList(path, replacements);
            if (lines.isEmpty()) return Component.empty();
            return Component.join(
                    net.kyori.adventure.text.JoinConfiguration.separator(Component.newline()),
                    lines);
        }

        String raw = resolve(path, replacements);
        return raw.isEmpty() ? Component.empty() : ColorUtil.formatComponent(raw);
    }

    /**
     * Многострочное сообщение: в lang-файле значение задаётся списком строк
     * (каждая — отдельная строка чата, пустая строка — отступ), а не одной
     * строкой с переводами \n. Одиночная строка тоже принимается — так
     * старые конфиги продолжают работать.
     *
     * <p>Отправка построчно — через {@link #sendList}.</p>
     */
    public List<Component> getMessageList(String path, String... replacements) {
        List<String> raw = langConfig.getStringList(path);
        if (raw.isEmpty()) {
            String single = resolve(path, replacements);
            return single.isEmpty() ? List.of() : List.of(ColorUtil.formatComponent(single));
        }

        String prefix = langConfig.getString("prefix", "");
        List<Component> lines = new ArrayList<>(raw.size());
        for (String line : raw) {
            // Пустая строка в списке — это осознанный отступ между блоками.
            String text = line == null ? "" : line.replace("%prefix%", prefix);
            lines.add(ColorUtil.formatComponent(applyReplacements(text, replacements)));
        }
        return lines;
    }

    /** Отправляет многострочное сообщение (см. {@link #getMessageList}). */
    public void sendList(CommandSender sender, String path, String... replacements) {
        for (Component line : getMessageList(path, replacements)) {
            sender.sendMessage(line);
        }
    }

    /** Как {@link #getMessage}, но legacy-строкой: многострочный список склеивается \n. */
    public String getRawMessage(String path, String... replacements) {
        if (langConfig.isList(path)) {
            List<String> raw = langConfig.getStringList(path);
            if (raw.isEmpty()) return "";
            String prefix = langConfig.getString("prefix", "");
            StringBuilder joined = new StringBuilder();
            for (String line : raw) {
                if (!joined.isEmpty()) joined.append('\n');
                String text = line == null ? "" : line.replace("%prefix%", prefix);
                joined.append(ColorUtil.formatLegacyString(applyReplacements(text, replacements)));
            }
            return joined.toString();
        }
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

    /**
     * Сырая строка шаблона с подстановками — без разбора MiniMessage и без
     * конвертации в legacy. Нужна, когда фрагмент (например, кнопку справки)
     * вставляют в другой шаблон: тот разбирается целиком, и теги клика
     * внутри вставленного фрагмента работают.
     */
    public String rawTemplate(String path, String... replacements) {
        return resolve(path, replacements);
    }

    private String resolve(String path, String... replacements) {
        String msg = langConfig.getString(path);
        if (msg == null) {
            warnMissing(path);
            return "";
        }
        if (msg.isEmpty()) return "";

        return applyReplacements(msg.replace("%prefix%", langConfig.getString("prefix", "")), replacements);
    }

    /** Ключи, о которых уже жаловались, — чтобы не заспамить лог при каждом сообщении. */
    private final java.util.Set<String> warnedMissing = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Отсутствующий ключ — почти всегда опечатка в коде или в lang-файле:
     * молчаливый пустой ответ тратит часы на отладку. Жалуемся один раз.
     */
    private void warnMissing(String path) {
        if (warnedMissing.add(path)) {
            plugin.getLogger().warning("Языковой ключ '" + path + "' не найден — сообщение не будет показано.");
        }
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
