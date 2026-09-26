package org.qweyns.qweprotectstones.diagnostics;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Проверки настроек, которые раньше молча подменялись значением по умолчанию.
 * Всё, что находит, пишет предупреждением — его подхватит {@link ConfigReport}.
 */
public final class ConfigValidator {

    private ConfigValidator() { }

    public static void validate(FileConfiguration cfg, Consumer<String> warn) {
        ConfigurationSection entities = cfg.getConfigurationSection("siege.explosion_types.entities");
        if (entities != null) {
            for (String key : entities.getKeys(false)) {
                if (!isEntityType(key)) warn.accept("siege.explosion_types.entities." + key + ": неизвестная сущность");
            }
        }

        for (String path : List.of("transfer.previous-owner-role", "transfer.admin-previous-owner-role", "market.sell.seller-role")) {
            String raw = cfg.getString(path, "");
            if (raw != null && !raw.isBlank() && TrustLevel.parse(raw).isEmpty()) {
                warn.accept(path + ": роль '" + raw + "' не найдена в roles.yml");
            }
        }

        oneOf(cfg, "help.no-args", "help", List.of("help", "menu"), warn);

        nonNegative(cfg, "creation.recreate-cooldown-seconds", warn);
        nonNegative(cfg, "creation.near-siege-radius", warn);
        nonNegative(cfg, "creation.spawn-radius", warn);
        nonNegative(cfg, "creation.forbidden-blocks-radius", warn);
    }

    static boolean isEntityType(String name) {
        try {
            EntityType.valueOf(name.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void oneOf(FileConfiguration cfg, String path, String fallback, List<String> allowed, Consumer<String> warn) {
        String value = cfg.getString(path, fallback);
        if (value == null) return;
        for (String option : allowed) if (option.equalsIgnoreCase(value)) return;
        warn.accept(path + ": '" + value + "' — ожидается одно из " + allowed);
    }

    private static void nonNegative(FileConfiguration cfg, String path, Consumer<String> warn) {
        if (cfg.contains(path) && cfg.getDouble(path) < 0) warn.accept(path + ": отрицательное значение, будет 0");
    }
}
