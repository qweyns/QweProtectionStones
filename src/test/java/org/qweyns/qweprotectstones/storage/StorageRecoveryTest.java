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
        r.restoreMember(new RegionMember(member,"member",TrustLevel.parse("build").orElseThrow(),123));
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
    @Test void disableAfterFailedStartupMustNotEraseRecoveryJournal() throws Exception {
        Path journal = folder.resolve("storage-recovery.yml");
        StorageRecovery.write(journal,List.of(StorageRecovery.command("deleteRegion",UUID.randomUUID())));
        String before = Files.readString(journal);
        var plugin = mock(org.qweyns.qweprotectstones.QweProtectStones.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        RegionStorage storage = new RegionStorage(plugin);
        RegionDao dao = mock(RegionDao.class);
        var daoField = RegionStorage.class.getDeclaredField("dao"); daoField.setAccessible(true); daoField.set(storage,dao);
        var blocked = RegionStorage.class.getDeclaredField("recoveryBlocked"); blocked.setAccessible(true); blocked.set(storage,true);
        storage.close();
        assertEquals(before,Files.readString(journal));
        verify(dao).close();
        verify(dao,never()).deleteAll(anyCollection());
    }
    @Test void persistedRecoveryPreservesControlCharactersAsStrings() throws Exception {
        Path journal = folder.resolve("storage-recovery.yml");
        Region region = new Region(UUID.randomUUID(),"world",new RegionBounds(0,0,0,2,2,2),1,1,1,
                "small",UUID.randomUUID(),"owner",5,10,1);
        String name = "start" + (char)0 + (char)0x85 + (char)0x2028 + "end";
        region.restoreDecoration(name);
        StorageRecovery.write(journal,List.of(StorageRecovery.region(region)));
        RegionDao dao = mock(RegionDao.class);
        doAnswer(call -> {
            Collection<Region> regions = call.getArgument(0);
            assertEquals(region.getDisplayName(),regions.iterator().next().getDisplayName());
            return null;
        }).when(dao).saveAll(anyCollection());
        StorageRecovery.replay(journal,dao);
        verify(dao).saveAll(anyCollection());
        assertFalse(Files.exists(journal));
    }
}
