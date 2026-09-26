package org.qweyns.qweprotectstones.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;

import java.util.regex.Pattern;

public final class ColorUtil {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final Pattern LEGACY_PATTERN = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])|&#([0-9a-fA-F]{6})");

    private ColorUtil() {
    }

    private static String legacyToTag(char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'k' -> "<obfuscated>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    public static Component formatComponent(String text) {
        if (text == null || text.isBlank()) return Component.empty();

        String preProcessed = LEGACY_PATTERN.matcher(text).replaceAll(match -> {
            if (match.group(2) != null) return "<#" + match.group(2) + ">";
            String tag = legacyToTag(Character.toLowerCase(match.group(1).charAt(0)));
            // $ и \ в замене спецсимволы, quoteReplacement
            return java.util.regex.Matcher.quoteReplacement(tag != null ? tag : match.group());
        });

        return MINI_MESSAGE.deserialize(preProcessed);
    }

    public static Component formatItemComponent(String text) {
        return formatComponent(text).decoration(TextDecoration.ITALIC, false);
    }

    public static String stripFormatting(String text) {
        if (text == null || text.isEmpty()) return "";
        return MiniMessage.miniMessage().stripTags(text)
                .replaceAll("\u00a7.", "");
    }

    /** Экранирует MiniMessage-разметку в чужом тексте (например, выводе PlaceholderAPI). */
    public static String escapeMini(String text) {
        if (text == null) return null;
        // экранируем сам слэш, затем угол: тег становится обычным текстом
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }

    public static String formatLegacyString(String text) {
        if (text == null || text.isBlank()) return "";
        return SECTION_SERIALIZER.serialize(formatComponent(text));
    }

    public static Color parseParticleColor(String hex) {
        if (hex == null || hex.isBlank()) return Color.WHITE;
        String value = hex.startsWith("#") ? hex.substring(1) : hex;
        if (value.length() != 6) return Color.WHITE;
        try {
            return Color.fromRGB(Integer.parseInt(value, 16));
        } catch (NumberFormatException e) {
            return Color.WHITE;
        }
    }
}
