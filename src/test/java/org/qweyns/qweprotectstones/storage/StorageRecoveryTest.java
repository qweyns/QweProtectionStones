package org.qweyns.qweprotectstones.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.qweyns.qweprotectstones.regions.*;
import org.qweyns.qweprotectstones.storage.dao.RegionDao;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageRecoveryTest {
    @TempDir Path folder;
    @Test void snapshotRoundTripPreservesPenaltyMembersAndEffects() {
        Region r = new Region(UUID.randomUUID(),"unloaded-world",new RegionBounds(-1,0,-1,1,10,1),0,5,0,
                "deleted-type",UUID.randomUUID(),"owner",5,10,1);
        UUID member = UUID.randomUUID(), banned = UUID.randomUUID();
        r.restoreMember(new RegionMember(member,"member",TrustLevel.BUILD,123));
        r.ban(banned,"banned"); r.setPenaltyUntil(5678); r.replaceEffect("SPEED",1);
        r.setFlag(RegionFlag.PVP,false); r.restoreStats(7,1234,"attacker");
        Region copy = StorageRecovery.decodeRegion(StorageRecovery.region(r));
        assertEquals(r.getId(),copy.getId()); assertEquals("unloaded-world",copy.getWorldName());
        assertEquals("deleted-type",copy.getTypeId()); assertEquals(5678,copy.getPenaltyUntil());
        assertEquals(123,copy.getMember(member).orElseThrow().addedAt());
        assertTrue(copy.isBanned(banned)); assertEquals(List.of("SPEED:1"),copy.getEffects());
        assertEquals(7,copy.getAttackCount()); assertEquals(Optional.of(false),copy.getFlagOverride(RegionFlag.PVP));
    }
    @Test void failedDeletionSurvivesRestartUntilDaoAcknowledges() throws Exception {
        Path journal = folder.resolve("storage-recovery.yml"); UUID id = UUID.randomUUID();
        StorageRecovery.write(journal,List.of(StorageRecovery.command("deleteRegion",id)));
        RegionDao dao = mock(RegionDao.class);
        doThrow(new IllegalStateException("DB down")).when(dao).deleteAll(anyCollection());
        assertThrows(java.io.IOException.class, () -> StorageRecovery.replay(journal,dao));
        assertTrue(Files.exists(journal));
        reset(dao);
        StorageRecovery.replay(journal,dao);
        verify(dao).deleteAll(List.of(id)); assertFalse(Files.exists(journal));
    }
}
