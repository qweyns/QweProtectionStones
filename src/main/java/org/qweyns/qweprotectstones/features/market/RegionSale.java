package org.qweyns.qweprotectstones.features.market;

import java.util.UUID;

/**
 * Приват, выставленный на продажу через {@code /ps sell}.
 *
 * @param regionId   идентификатор привата
 * @param sellerId   владелец-продавец
 * @param sellerName его имя на момент выставления
 * @param price      цена ({@code market.sell.max-price} ограничивает сверху)
 * @param createdAt  когда выставлено
 */
public record RegionSale(UUID regionId, UUID sellerId, String sellerName, double price, long createdAt) {
}
