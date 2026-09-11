package org.qweyns.qweprotectstones.features.importer;

import org.qweyns.qweprotectstones.regions.RegionFlag;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class WgFlags {

    private WgFlags() {
    }

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

    public static Boolean mapValue(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "allow", "true" -> Boolean.TRUE;
            case "deny", "false" -> Boolean.FALSE;
            default -> null;
        };
    }

    public static RegionFlag resolve(String ourName) {
        if (ourName == null) return null;
        try {
            return RegionFlag.valueOf(ourName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Map<String, String> normalize(Map<String, String> raw) {
        return raw.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .collect(Collectors.toMap(
                        e -> e.getKey().trim().toLowerCase(Locale.ROOT),
                        e -> e.getValue().trim(),
                        (a, b) -> b));
    }
}
