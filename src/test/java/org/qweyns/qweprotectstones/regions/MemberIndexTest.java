package org.qweyns.qweprotectstones.regions;

import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.storage.RegionStorage;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberIndexTest {

    private static Region region(UUID owner, int x) {
        return new Region(UUID.randomUUID(), "world", new RegionBounds(x, 0, 0, x + 10, 100, 10),
                x + 5, 50, 5, "small", owner, "owner", 1, 10, System.currentTimeMillis());
    }

    @Test
    void accessibleRegionsFollowMembershipChanges() {
        QweProtectStones plugin = mock(QweProtectStones.class);
        when(plugin.getRegionStorage()).thenReturn(mock(RegionStorage.class));
        RegionManager manager = new RegionManager(plugin);

        UUID owner = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        Region first = region(owner, 0);
        Region second = region(owner, 100);
        first.setMember(friend, "friend", TrustLevel.parse("build").orElseThrow());
        assertTrue(manager.importRegion(first));
        assertTrue(manager.importRegion(second));

        assertEquals(1, manager.getAccessibleRegions(friend).size(), "участник, добавленный до регистрации");

        second.setMember(friend, "friend", TrustLevel.parse("access").orElseThrow());
        assertEquals(2, manager.getAccessibleRegions(friend).size());

        first.removeMember(friend);
        assertEquals(1, manager.getAccessibleRegions(friend).size());

        second.ban(friend, "friend");
        assertTrue(manager.getAccessibleRegions(friend).isEmpty());

        assertEquals(2, manager.getAccessibleRegions(owner).size(), "владелец видит свои приваты");
    }

    @Test
    void indexVersionChangesWhenRegionsChange() {
        QweProtectStones plugin = mock(QweProtectStones.class);
        when(plugin.getRegionStorage()).thenReturn(mock(RegionStorage.class));
        RegionManager manager = new RegionManager(plugin);
        long before = manager.indexVersion();
        manager.importRegion(region(UUID.randomUUID(), 0));
        assertNotEquals(before, manager.indexVersion());
    }
}
