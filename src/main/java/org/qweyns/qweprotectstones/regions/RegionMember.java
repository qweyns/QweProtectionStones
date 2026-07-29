package org.qweyns.qweprotectstones.regions;

import java.util.UUID;

/**
 * Участник привата. Ник хранится рядом с UUID, чтобы показывать список
 * оффлайн-игроков без обращения к Mojang API.
 */
public record RegionMember(UUID uuid, String name, TrustLevel trust, long addedAt) {

    public RegionMember withTrust(TrustLevel newTrust) {
        return new RegionMember(uuid, name, newTrust, addedAt);
    }

    public RegionMember withName(String newName) {
        return new RegionMember(uuid, newName, trust, addedAt);
    }

    public String displayName() {
        return name != null && !name.isBlank() ? name : uuid.toString().substring(0, 8);
    }
}
