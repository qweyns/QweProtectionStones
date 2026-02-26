package org.qweyns.pshologramm.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;

public final class ColorUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .build();

    private ColorUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Component formatComponent(String text) {
        if (text == null || text.isBlank()) {
            return Component.empty();
        }

        if (text.contains("<") && text.contains(">")) {
            return MM.deserialize(text);
        }

        return LEGACY_AMPERSAND.deserialize(text);
    }

    public static String formatLegacyString(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return LEGACY_SECTION.serialize(formatComponent(text));
    }

    public static Color parseParticleColor(String hex) {
        if (hex == null || hex.isBlank()) return Color.WHITE;

        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

        if (hex.length() != 6) return Color.WHITE;

        try {
            return Color.fromRGB(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return Color.WHITE;
        }
    }
}
