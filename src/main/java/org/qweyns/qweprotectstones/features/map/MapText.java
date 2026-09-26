package org.qweyns.qweprotectstones.features.map;

import org.qweyns.qweprotectstones.QweProtectStones;

public final class MapText {

    private MapText() {
    }

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

    // экранируются только подставляемые значения: разметку шаблона
    // (например <br/>) администратор задал осознанно
    public static String line(QweProtectStones plugin, String key, String... replacements) {
        String raw = plugin.getLanguageManager().rawTemplate(key);
        if (raw == null || raw.isEmpty()) return "";
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            if (replacements[i] == null) continue;
            raw = raw.replace(replacements[i], escapeHtml(String.valueOf(replacements[i + 1])));
        }
        return raw;
    }
}
