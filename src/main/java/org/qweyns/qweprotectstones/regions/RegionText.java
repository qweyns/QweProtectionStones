package org.qweyns.qweprotectstones.regions;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.regex.Pattern;

/**
 * Разбор пользовательских текстов привата — названия, приветствия, прощания.
 *
 * <p>Вынесено из {@link Region} отдельно и намеренно не зависит от Bukkit:
 * так правила «что считается пустым» и «где обрезать» покрыты юнит-тестами,
 * а не проверяются вручную на живом сервере.</p>
 */
public final class RegionText {

    /** Потолок на длину — и для колонок базы, и против «простыней» в чате. */
    public static final int MAX_LENGTH = 128;

    private RegionText() {
    }

    /** Цветовой код, скопированный из чата: § и, возможно, следующий символ-код. */
    private static final Pattern SECTION_CODE = Pattern.compile("\u00a7([0-9a-fk-orA-FK-OR])?");

    /**
     * Приводит текст к безопасному виду: вырезает разметку (иначе название или
     * приветствие превращаются в кликабельную ловушку для входящих или в скрипт
     * на веб-карте), убирает переводы строк, обрезает пробелы и лишнюю длину.
     *
     * <p>MiniMessage-теги вырезаются вместе с содержимым — от {@code <click:...>жми</click>}
     * остаётся слово «жми»; обычный текст, не похожий на тег (например, «&lt;3»), не трогается.
     * Символ § и следующий за ним код цвета удаляются как пара.</p>
     *
     * @return нормализованный текст; пустая строка означает «не задано»
     */
    public static String normalize(String value) {
        if (value == null) return "";

        String cleaned = MiniMessage.miniMessage().stripTags(value);
        cleaned = SECTION_CODE.matcher(cleaned).replaceAll("");
        // \r\n — это один перевод строки, а не два пробела.
        cleaned = cleaned.replace("\r\n", " ")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned;
    }

    /**
     * Название для показа в голограмме, меню и на карте.
     *
     * @param displayName заданное владельцем название, может быть пустым
     * @param ownerName   ник владельца, может быть пустым
     * @param shortId     короткий идентификатор — последний запасной вариант
     */
    public static String label(String displayName, String ownerName, String shortId) {
        if (displayName != null && !displayName.isEmpty()) return displayName;
        if (ownerName != null && !ownerName.isEmpty()) return ownerName;
        return shortId == null ? "" : shortId;
    }

    /** Считается ли слово командой «стереть текст». */
    public static boolean isClearWord(String value) {
        if (value == null) return false;

        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "clear", "reset", "off", "none", "сброс", "убрать" -> true;
            default -> false;
        };
    }
}
