package org.qweyns.qweprotectstones.regions;

import java.util.Locale;
import java.util.Optional;

public enum TrustLevel {

    ACCESS(1),

    CONTAINER(2),

    BUILD(3),

    MANAGER(4),

    OWNER(5);

    private final int weight;

    TrustLevel(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    public boolean atLeast(TrustLevel required) {
        return weight >= required.weight;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<TrustLevel> parse(String raw) {
        if (raw == null) return Optional.empty();
        for (TrustLevel level : values()) {
            if (level.name().equalsIgnoreCase(raw.trim())) return Optional.of(level);
        }
        return Optional.empty();
    }

    public static TrustLevel[] grantable() {
        return new TrustLevel[]{ACCESS, CONTAINER, BUILD, MANAGER};
    }
}
