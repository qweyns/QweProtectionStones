package org.qweyns.qweprotectstones.regions;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.regex.Pattern;

public final class RegionText {

    public static final int MAX_LENGTH = 128;

    private RegionText() {
    }

    private static final Pattern SECTION_CODE = Pattern.compile("\u00a7([0-9a-fk-orA-FK-OR])?");

    public static String normalize(String value) {
        if (value == null) return "";

        String cleaned = MiniMessage.miniMessage().stripTags(value);
        cleaned = SECTION_CODE.matcher(cleaned).replaceAll("");

        cleaned = cleaned.replace("\r\n", " ")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned;
    }

    public static String label(String displayName, String ownerName, String shortId) {
        if (displayName != null && !displayName.isEmpty()) return displayName;
        if (ownerName != null && !ownerName.isEmpty()) return ownerName;
        return shortId == null ? "" : shortId;
    }

    public static boolean isClearWord(String value) {
        if (value == null) return false;

        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "clear", "reset", "off", "none", "сброс", "убрать" -> true;
            default -> false;
        };
    }
}
