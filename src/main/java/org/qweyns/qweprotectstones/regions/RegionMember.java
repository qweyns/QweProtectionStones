package org.qweyns.qweprotectstones.regions;

import java.util.UUID;

public record RegionMember(UUID uuid, String name, TrustLevel trust, long addedAt) {

    public String displayName() {
        return name != null && !name.isBlank() ? name : uuid.toString().substring(0, 8);
    }
}
