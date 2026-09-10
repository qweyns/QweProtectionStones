package org.qweyns.qweprotectstones.regions;

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

    /**
     * Приводит текст к безопасному виду: убирает переводы строк (иначе одна
     * запись разъезжается на несколько), обрезает пробелы и лишнюю длину.
     *
     * @return нормализованный текст; пустая строка означает «не задано»
     */
    public static String normalize(String value) {
        if (value == null) return "";

        // \r\n — это один перевод строки, а не два пробела.
        String cleaned = value.replace("\r\n", " ")
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
