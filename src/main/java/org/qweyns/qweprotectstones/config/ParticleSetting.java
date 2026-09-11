package org.qweyns.qweprotectstones.config;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

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

    public boolean supportsColor() {
        return particle == Particle.DUST;
    }

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

    public double step(double length) {
        double base;
        if (length > 128) base = 8.0;
        else if (length > 64) base = 4.0;
        else if (length > 32) base = 2.0;
        else base = 1.0;

        return Math.max(0.25, base * density);
    }

    public void spawnFor(Player player, List<Location> points, Color color) {
        Object data = supportsColor() ? new Particle.DustOptions(color, size) : null;
        for (Location point : points) {
            player.spawnParticle(particle, point, 1, 0, 0, 0, 0, data);
        }
    }

    public void spawnIn(World world, List<Location> points, Color color) {
        Object data = supportsColor() ? new Particle.DustOptions(color, size) : null;
        for (Location point : points) {
            world.spawnParticle(particle, point, 1, 0, 0, 0, 0, data);
        }
    }
}
