package org.qweyns.qweprotectstones.config;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Звук из конфига в виде {@code ЗВУК:громкость:высота}.
 *
 * <p>Достаточно написать {@code ENTITY_VILLAGER_NO} — громкость и высота
 * подставятся по умолчанию. Значение {@code none} или {@code off} выключает
 * звук совсем: раньше отключить его было нельзя вообще. Пустая строка или
 * отсутствующий ключ означают «оставить значение по умолчанию».</p>
 *
 * @param sound  сам звук; {@code null} означает «не проигрывать»
 * @param volume громкость (она же радиус слышимости)
 * @param pitch  высота тона, 0.5–2.0
 */
public record SoundSetting(Sound sound, float volume, float pitch) {

    /** Заглушка для выключенного звука — вместо разбросанных проверок на null. */
    public static final SoundSetting NONE = new SoundSetting(null, 0f, 0f);

    public boolean isEnabled() {
        return sound != null;
    }

    /**
     * Разбирает строку конфига.
     *
     * @param raw      значение из конфига
     * @param fallback что вернуть, если строка пустая или звук не распознан
     */
    public static SoundSetting parse(String raw, SoundSetting fallback) {
        if (raw == null) return fallback;

        String value = raw.trim();
        if (value.isEmpty()) return fallback;

        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("none") || lower.equals("off") || lower.equals("false")) return NONE;

        String[] parts = value.split(":");
        Sound sound = resolve(parts[0]);
        if (sound == null) return fallback;

        return new SoundSetting(sound,
                readFloat(parts, 1, fallback.isEnabled() ? fallback.volume() : 1f),
                readFloat(parts, 2, fallback.isEnabled() ? fallback.pitch() : 1f));
    }

    /** Принимает и {@code BLOCK_ANVIL_USE}, и {@code block.anvil.use}. */
    private static Sound resolve(String name) {
        try {
            return Sound.valueOf(name.trim().toUpperCase(Locale.ROOT).replace('.', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static float readFloat(String[] parts, int index, float fallback) {
        if (parts.length <= index) return fallback;
        try {
            return Float.parseFloat(parts[index].trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Звук слышит только этот игрок — для меню и личных оповещений. */
    public void playTo(Player player) {
        if (sound == null || player == null) return;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /** Звук слышат все вокруг точки — для рейдов и установки привата. */
    public void playAt(Location location) {
        if (sound == null || location == null) return;

        World world = location.getWorld();
        if (world != null) world.playSound(location, sound, volume, pitch);
    }
}
