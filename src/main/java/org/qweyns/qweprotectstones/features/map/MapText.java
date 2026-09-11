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

    public static String line(QweProtectStones plugin, String key, String... replacements) {
        return escapeHtml(plugin.getLanguageManager().rawTemplate(key, replacements));
    }
}
