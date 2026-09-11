package org.qweyns.qweprotectstones.config;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
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
     * @param fallback что вернуть, если строка пустая или звук не распознан;
     *                 {@code null} допустим — тогда громкость и высота для
     *                 частично заданной строки берутся стандартные (1.0)
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

        // Fallback бывает null: Tunables так отличает «ключа нет» от
        // «звук задан». Стандартные громкость и высота — 1.0.
        boolean hasFallback = fallback != null && fallback.isEnabled();
        return new SoundSetting(sound,
                readFloat(parts, 1, hasFallback ? fallback.volume() : 1f),
                readFloat(parts, 2, hasFallback ? fallback.pitch() : 1f));
    }

    /**
     * Принимает ключ реестра ({@code block.anvil.use}, {@code myplugin:custom}),
     * прежний формат имён констант ({@code BLOCK_ANVIL_USE}) и {@code none}.
     * Возвращает {@code NONE} вместо null, чтобы его можно было безопасно играть.
     */
    public static Sound resolve(String name) {
        String raw = name.trim().toLowerCase(Locale.ROOT);

        Sound sound = byKey(raw);
        // Старые конфиги писали имена констант: ENTITY_PLAYER_LEVELUP → entity.player.levelup.
        if (sound == null) sound = byKey(raw.replace('_', '.'));
        return sound;
    }

    /** Звук по ключу реестра: заменяет {@code Sound.valueOf}, помеченное к удалению. */
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
