package org.qweyns.qweprotectstones.config;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Чем и как густо рисуются границы приватов.
 *
 * <p>Раньше это был жёстко зашитый {@code DUST} размера 1.5 с шагом, который
 * нельзя было тронуть. Теперь тип частицы, её размер, плотность сетки и потолок
 * точек берутся из конфига — на слабом сервере плотность можно занизить.</p>
 *
 * @param particle  тип частицы; цвет учитывается только у {@code DUST}
 * @param size      размер частицы (для {@code DUST})
 * @param density   множитель шага сетки: {@code 0.5} — вдвое плотнее, {@code 2.0} — вдвое реже
 * @param maxPoints потолок числа точек на один каркас
 */
public record ParticleSetting(Particle particle, float size, double density, int maxPoints) {

    public static final ParticleSetting DEFAULT = new ParticleSetting(Particle.DUST, 1.5f, 1.0, 2_000);

    public ParticleSetting {
        if (particle == null) particle = Particle.DUST;
        size = (float) clamp(size, 0.1, 10.0);
        density = clamp(density, 0.25, 8.0);
        maxPoints = (int) clamp(maxPoints, 64, 20_000);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Цвет умеет показывать только DUST — для остальных частиц он игнорируется. */
    public boolean supportsColor() {
        return particle == Particle.DUST;
    }

    /**
     * Разбирает секцию конфига.
     *
     * @param type    имя частицы; неизвестное значение откатывается к {@code DUST}
     * @param onBadType вызывается с именем частицы, если она не распознана
     */
    public static ParticleSetting of(String type, double size, double density, int maxPoints,
                                     java.util.function.Consumer<String> onBadType) {
        Particle particle = Particle.DUST;
        if (type != null && !type.isBlank()) {
            try {
                particle = Particle.valueOf(type.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                if (onBadType != null) onBadType.accept(type);
            }
        }
        return new ParticleSetting(particle, (float) size, density, maxPoints);
    }

    /** Шаг сетки с учётом плотности: длинные рёбра прорежаются сильнее. */
    public double step(double length) {
        double base;
        if (length > 128) base = 8.0;
        else if (length > 64) base = 4.0;
        else if (length > 32) base = 2.0;
        else base = 1.0;

        return Math.max(0.25, base * density);
    }

    /** Показать точки одному игроку — предпросмотр и личная подсветка. */
    public void spawnFor(Player player, List<Location> points, Color color) {
        Object data = supportsColor() ? new Particle.DustOptions(color, size) : null;
        for (Location point : points) {
            player.spawnParticle(particle, point, 1, 0, 0, 0, 0, data);
        }
    }

    /** Показать точки всем в мире — общая подсветка границ. */
    public void spawnIn(World world, List<Location> points, Color color) {
        Object data = supportsColor() ? new Particle.DustOptions(color, size) : null;
        for (Location point : points) {
            world.spawnParticle(particle, point, 1, 0, 0, 0, 0, data);
        }
    }
}
