package org.qweyns.qweprotectstones.config;

import org.bukkit.configuration.ConfigurationSection;

/** Настройки администратора ограничиваются безопасными диапазонами, а NaN/Infinity не проходят в Bukkit. */
public final class ConfigValues {
    private ConfigValues() { }
    public static long boundedLong(ConfigurationSection config, String key, long fallback, long min, long max) {
        return Math.max(min, Math.min(max, config.getLong(key, fallback)));
    }
    public static double boundedDouble(ConfigurationSection config, String key, double fallback, double min, double max) {
        double value = config.getDouble(key, fallback);
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
