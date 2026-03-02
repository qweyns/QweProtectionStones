package org.qweyns.pshologramm.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;

import java.util.Map;
import java.util.regex.Pattern;

public class ColorUtil {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final Pattern LEGACY_PATTERN = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])|&#([0-9a-fA-F]{6})");

    private static final Map<Character, String> COLOR_MAP = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"), Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"), Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"), Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"), Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"), Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"), Map.entry('l', "<bold>"), Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"), Map.entry('o', "<italic>"), Map.entry('k', "<obfuscated>"),
            Map.entry('r', "<reset>")
    );

    public static Component formatComponent(String text) {
        if (text == null || text.isBlank()) return Component.empty();

        String preProcessed = LEGACY_PATTERN.matcher(text).replaceAll(match -> {
            if (match.group(2) != null) return "<#" + match.group(2) + ">";
            char code = Character.toLowerCase(match.group(1).charAt(0));
            return COLOR_MAP.getOrDefault(code, match.group());
        });

        return MINI_MESSAGE.deserialize(preProcessed);
    }

    public static String formatLegacyString(String text) {
        if (text == null || text.isBlank()) return "";
        return SECTION_SERIALIZER.serialize(formatComponent(text));
    }

    public static Color parseParticleColor(String hex) {
        if (hex == null || hex.isBlank()) return Color.WHITE;
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() != 6) return Color.WHITE;
        try { return Color.fromRGB(Integer.parseInt(hex, 16)); }
        catch (NumberFormatException e) { return Color.WHITE; }
    }
}
