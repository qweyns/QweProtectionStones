package org.qweyns.qweprotectstones.features.market;

import java.util.UUID;

public record RegionSale(UUID regionId, UUID sellerId, String sellerName, double price, long createdAt) {
}
