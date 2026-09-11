package org.qweyns.qweprotectstones.config;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Locale;

public record SoundSetting(Sound sound, float volume, float pitch) {

    public static final SoundSetting NONE = new SoundSetting(null, 0f, 0f);

    public boolean isEnabled() {
        return sound != null;
    }

    public static SoundSetting parse(String raw, SoundSetting fallback) {
        if (raw == null) return fallback;

        String value = raw.trim();
        if (value.isEmpty()) return fallback;

        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("none") || lower.equals("off") || lower.equals("false")) return NONE;

        String[] parts = value.split(":");
        Sound sound = resolve(parts[0]);
        if (sound == null) return fallback;

        // null = ключа нет, иначе задан, но не распознан

        boolean hasFallback = fallback != null && fallback.isEnabled();
        return new SoundSetting(sound,
                readFloat(parts, 1, hasFallback ? fallback.volume() : 1f),
                readFloat(parts, 2, hasFallback ? fallback.pitch() : 1f));
    }

    public static Sound resolve(String name) {
        String raw = name.trim().toLowerCase(Locale.ROOT);

        Sound sound = byKey(raw);
        // Старые конфиги писали имена констант: ENTITY_PLAYER_LEVELUP → entity.player.levelup.
        if (sound == null) sound = byKey(raw.replace('_', '.'));
        return sound;
    }

    private static Sound byKey(String key) {
        NamespacedKey namespaced = NamespacedKey.fromString(key);
        return namespaced != null ? Registry.SOUNDS.get(namespaced) : null;
    }

    private static float readFloat(String[] parts, int index, float fallback) {
        if (parts.length <= index) return fallback;
        try {
            return Float.parseFloat(parts[index].trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void playTo(Player player) {
        if (sound == null || player == null) return;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public void playAt(Location location) {
        if (sound == null || location == null) return;

        World world = location.getWorld();
        if (world != null) world.playSound(location, sound, volume, pitch);
    }
}
