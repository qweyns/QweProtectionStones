package org.qweyns.qweprotectstones.features.market;

import java.util.UUID;

public record RegionRental(UUID regionId, UUID ownerId, String ownerName,
                           double price, int durationMinutes,
                           UUID tenantId, String tenantName, long rentedUntil) {

    public boolean isRented() {
        return tenantId != null && rentedUntil > System.currentTimeMillis();
    }
}
