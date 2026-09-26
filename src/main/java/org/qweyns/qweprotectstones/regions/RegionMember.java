package org.qweyns.qweprotectstones.regions;

import java.util.UUID;

/**
 * Участник привата. Роль хранится id-строкой и разрешается через реестр ролей
 * при каждом обращении — после /reload участник сразу получает новые права роли,
 * а удалённая из конфига роль не теряется при записи в базу.
 */
public record RegionMember(UUID uuid, String name, String role, long addedAt) {

    public RegionMember {
        role = TrustLevel.normalize(role);
    }

    public RegionMember(UUID uuid, String name, TrustLevel trust, long addedAt) {
        this(uuid, name, trust == null ? TrustLevel.defaultRole().id() : trust.id(), addedAt);
    }

    public TrustLevel trust() {
        return TrustLevel.ofStored(role);
    }

    public String displayName() {
        return name != null && !name.isBlank() ? name : uuid.toString().substring(0, 8);
    }
}
