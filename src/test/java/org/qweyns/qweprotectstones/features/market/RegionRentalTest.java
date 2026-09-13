package org.qweyns.qweprotectstones.features.market;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionRentalTest {

    private static RegionRental rental(UUID tenantId, long rentedUntil) {
        return new RegionRental(UUID.randomUUID(), UUID.randomUUID(), "Steve",
                100.0, 30, tenantId, tenantId == null ? null : "Alex", rentedUntil);
    }

    @Test
    void арендаДействует() {
        assertTrue(rental(UUID.randomUUID(), System.currentTimeMillis() + 60_000).isRented(),
                "срок в будущем и арендатор задан — занято");
    }

    @Test
    void арендаИстекла() {
        assertFalse(rental(UUID.randomUUID(), System.currentTimeMillis() - 1_000).isRented(),
                "срок прошёл — объявление снова свободно");
    }

    @Test
    void безАрендатораСвободно() {
        assertFalse(rental(null, System.currentTimeMillis() + 60_000).isRented(),
                "арендатора нет — свободно, даже если срок задан");
        assertFalse(rental(null, 0).isRented());
    }
}
