package org.qweyns.qweprotectstones.features.map;

import org.qweyns.qweprotectstones.QweProtectStones;

/**
 * Текст для веб-карт (Dynmap, BlueMap): подписи маркеров из lang-файла и
 * экранирование HTML.
 *
 * <p>Всплывающие подсказки маркеров обе карты рендерят как HTML в браузере,
 * поэтому любое значение — в том числе заданное игроком название привата —
 * обязано пройти через {@link #escapeHtml(String)}. Иначе название вида
 * {@code <img src=x onerror=...>} превращается в скрипт на странице карты.</p>
 */
public final class MapText {

    private MapText() {
    }

    /** Символы, значимые для HTML, — в безопасные последовательности. */
    public static String escapeHtml(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            switch (raw.charAt(i)) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(raw.charAt(i));
            }
        }
        return sb.toString();
    }

    /**
     * Строка подписи из lang-файла с подстановками — уже экранированная для
     * HTML: локализация тоже не должна открывать разметку на странице карты.
     */
    public static String line(QweProtectStones plugin, String key, String... replacements) {
        return escapeHtml(plugin.getLanguageManager().getRawMessage(key, replacements));
    }
}
