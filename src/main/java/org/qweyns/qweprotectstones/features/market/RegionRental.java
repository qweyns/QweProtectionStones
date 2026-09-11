package org.qweyns.qweprotectstones.features.market;

import java.util.UUID;

/**
 * Условия аренды привата: {@code /ps rent <цена> <минуты>} публикует их,
 * {@code /ps rent take} снимает приват на период.
 *
 * @param regionId       идентификатор привата
 * @param ownerId        владелец, сдающий в аренду
 * @param ownerName      его имя
 * @param price          цена за один период аренды
 * @param durationMinutes длительность одного периода (в минутах)
 * @param tenantId       текущий арендатор или null, если свободно
 * @param tenantName     имя арендатора
 * @param rentedUntil    до какого момента действует аренда (0 — не занято)
 */
public record RegionRental(UUID regionId, UUID ownerId, String ownerName,
                           double price, int durationMinutes,
                           UUID tenantId, String tenantName, long rentedUntil) {

    /** Арендован ли приват прямо сейчас. */
    public boolean isRented() {
        return tenantId != null && rentedUntil > System.currentTimeMillis();
    }
}
