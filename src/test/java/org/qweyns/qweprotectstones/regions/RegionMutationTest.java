package org.qweyns.qweprotectstones.regions;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RegionMutationTest {
    static Region region() {
        return new Region(UUID.randomUUID(), "world", new RegionBounds(-2, 0, -2, 2, 10, 2), 0, 5, 0,
                "small", UUID.randomUUID(), "owner", 5, 10, 1);
    }
    @Test void reservationRejectsReentryAndReleases() {
        Region region = region();
        Region.Operation op = region.tryOperation();
        assertNotNull(op);
        assertNull(region.tryOperation());
        assertTrue(op.owns(region));
        op.close();
        assertFalse(op.owns(region));
        Region.Operation second = region.tryOperation();
        assertNotNull(second);
        op.close();
        assertTrue(second.owns(region));
        second.close();
    }
    @Test void ownershipUnbansNewOwnerAndInvalidatesCache() {
        Region r = region();
        UUID newOwner = UUID.randomUUID();
        r.ban(newOwner, "new");
        long version = r.getVersion();
        r.transferOwnership(newOwner, "new");
        assertFalse(r.isBanned(newOwner));
        assertEquals(TrustLevel.OWNER, r.getTrust(newOwner));
        assertTrue(r.getVersion() > version);
    }
    @Test void snapshotDoesNotTrackLaterMutations() {
        Region r = region();
        r.setMember(UUID.randomUUID(), "friend", TrustLevel.BUILD);
        r.replaceEffect("SPEED", 1);
        Region copy = r.snapshot();
        r.setDurability(1);
        r.setBounds(new RegionBounds(10, 0, 10, 20, 10, 20));
        r.replaceEffect("SPEED", 2);
        assertEquals(5, copy.getDurability());
        assertEquals(-2, copy.getBounds().minX());
        assertEquals(java.util.List.of("SPEED:1"), copy.getEffects());
        assertEquals(1, copy.getMemberCount());
    }
    @Test void allDisplayMutationsInvalidateVersion() {
        Region r = region();
        long version = r.getVersion();
        r.setBounds(r.getBounds()); assertTrue(r.getVersion() > version); version = r.getVersion();
        r.setTypeId("other"); assertTrue(r.getVersion() > version); version = r.getVersion();
        r.setMaxDurability(20); assertTrue(r.getVersion() > version); version = r.getVersion();
        r.setPenaltyUntil(100); assertTrue(r.getVersion() > version);
    }
}
