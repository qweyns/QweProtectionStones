package org.qweyns.qweprotectstones.features.importer;

import org.qweyns.qweprotectstones.regions.RegionFlag;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Маппинг флагов WorldGuard (и, соответственно, ProtectionStones — его регионы
 * и есть регионы WorldGuard) на флаги QweProtectStones при импорте.
 *
 * <p>Класс намеренно без Bukkit — чистые функции, которые покрываются
 * юнит-тестами. Само сопоставление имён настраивается в config.yml
 * ({@code import.worldguard.flag-mapping}), здесь — лишь разумные значения
 * по умолчанию.</p>
 */
public final class WgFlags {

    private WgFlags() {
    }

    /**
     * Сопоставление по умолчанию: имя флага WorldGuard (в нижнем регистре) →
     * имя нашего флага. Ключи со строковыми значениями (greeting, farewell)
     * не переносим — у нас флаги логические.
     */
    public static final Map<String, String> DEFAULT_MAPPING = Map.ofEntries(
            Map.entry("pvp", "PVP"),
            Map.entry("entry", "ENTRY"),
            Map.entry("mob-spawning", "MONSTER_SPAWNING"),
            Map.entry("mob-griefing", "MOB_GRIEFING"),
            Map.entry("fire-spread", "FIRE_SPREAD"),
            Map.entry("pistons", "PISTONS_FROM_OUTSIDE"),
            Map.entry("tnt", "EXPLOSION_DAMAGE"),
            Map.entry("creeper-explosion", "EXPLOSION_DAMAGE"),
            Map.entry("other-explosion", "EXPLOSION_DAMAGE"));

    /**
     * Значение флага WorldGuard → наш boolean.
     *
     * @return {@code null}, если флаг не задан или значение не логическое
     *         (строки вроде текста приветствия) — такие пропускаем
     */
    public static Boolean mapValue(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "allow", "true" -> Boolean.TRUE;
            case "deny", "false" -> Boolean.FALSE;
            default -> null;
        };
    }

    /** Имя флага WorldGuard → наш флаг; {@code null}, если флага с таким именем нет. */
    public static RegionFlag resolve(String ourName) {
        if (ourName == null) return null;
        try {
            return RegionFlag.valueOf(ourName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Нормализует пользовательский маппинг из конфига: ключи в нижнем регистре. */
    public static Map<String, String> normalize(Map<String, String> raw) {
        return raw.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .collect(Collectors.toMap(
                        e -> e.getKey().trim().toLowerCase(Locale.ROOT),
                        e -> e.getValue().trim(),
                        (a, b) -> b));
    }
}
