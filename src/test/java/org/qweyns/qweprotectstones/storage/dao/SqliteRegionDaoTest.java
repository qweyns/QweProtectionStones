package org.qweyns.qweprotectstones.storage.dao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.features.market.RegionRental;
import org.qweyns.qweprotectstones.features.market.RegionSale;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SqliteRegionDaoTest {

    private SqliteRegionDao dao;
    private File dbFile;

    @BeforeEach
    void setUp() throws IOException {
        dbFile = File.createTempFile("qps-dao-test", ".db");
        dao = new SqliteRegionDao(dbFile);
        dao.init();
    }

    @AfterEach
    void tearDown() {
        dao.close();
        if (!dbFile.delete()) dbFile.deleteOnExit();
    }

    private Region sampleRegion() {
        Region region = new Region(UUID.randomUUID(), "world",
                new RegionBounds(-10, 0, -10, 10, 60, 10),
                0, 32, 0, "small",
                UUID.randomUUID(), "Owner", 50, 100, System.currentTimeMillis());
        region.setMember(UUID.randomUUID(), "Friend", TrustLevel.BUILD);
        region.setMember(UUID.randomUUID(), "Manager", TrustLevel.MANAGER);
        region.setFlag(RegionFlag.PVP, true);
        region.ban(UUID.randomUUID(), "Griefer");
        region.setDisplayName("База");
        region.setGreeting("Добро пожаловать");
        region.setPenaltyUntil(System.currentTimeMillis() + 3_600_000L);
        return region;
    }

    @Test
    void saveAndLoadRegionRoundtrip() {
        Region region = sampleRegion();
        dao.saveAll(List.of(region));

        List<Region> loaded = dao.loadAll();
        assertEquals(1, loaded.size());

        Region restored = loaded.get(0);
        assertEquals(region.getId(), restored.getId());
        assertEquals("world", restored.getWorldName());
        assertEquals(region.getBounds(), restored.getBounds());
        assertEquals(region.getCoreX(), restored.getCoreX());
        assertEquals(region.getCoreY(), restored.getCoreY());
        assertEquals(region.getCoreZ(), restored.getCoreZ());
        assertEquals("small", restored.getTypeId());
        assertEquals(region.getOwnerId(), restored.getOwnerId());
        assertEquals("Owner", restored.getOwnerName());
        assertEquals(50, restored.getDurability());
        assertEquals(100, restored.getMaxDurability());
        assertEquals("База", restored.getDisplayName());
        assertEquals("Добро пожаловать", restored.getGreeting());
        assertEquals(region.getPenaltyUntil(), restored.getPenaltyUntil());
    }

    @Test
    void membersFlagsAndBansSurviveRoundtrip() {
        Region region = sampleRegion();
        UUID friendId = region.getMembers().stream()
                .filter(m -> m.trust() == TrustLevel.BUILD)
                .findFirst().orElseThrow().uuid();

        dao.saveAll(List.of(region));

        Region restored = dao.loadAll().get(0);
        assertEquals(2, restored.getMemberCount());
        assertEquals(TrustLevel.BUILD, restored.getTrust(friendId));
        assertEquals(1, restored.getFlagOverrides().size());
        assertEquals(Boolean.TRUE, restored.getFlagOverride(RegionFlag.PVP).orElse(null));
        assertEquals(1, restored.getBannedPlayers().size());
    }

    @Test
    void updateOverwritesInsteadOfDuplicating() {
        Region region = sampleRegion();
        dao.saveAll(List.of(region));

        region.setDurability(75);
        region.setMember(UUID.randomUUID(), "NewFriend", TrustLevel.ACCESS);
        dao.saveAll(List.of(region));

        List<Region> loaded = dao.loadAll();
        assertEquals(1, loaded.size());
        assertEquals(75, loaded.get(0).getDurability());
        assertEquals(3, loaded.get(0).getMemberCount());
    }

    @Test
    void deleteAllRemovesRegionCompletely() {
        Region region = sampleRegion();
        dao.saveAll(List.of(region));
        dao.deleteAll(List.of(region.getId()));

        assertTrue(dao.loadAll().isEmpty());
    }

    @Test
    void logRoundtripKeepsOrder() {
        UUID regionId = UUID.randomUUID();
        dao.appendLog(List.of(
                new RegionLogEntry(regionId, 1_000L, "Steve", "attack", "-1"),
                new RegionLogEntry(regionId, 2_000L, "Alex", "break", null)));

        List<RegionLogEntry> entries = dao.readLog(regionId, 10);
        assertEquals(2, entries.size());
        assertEquals("break", entries.get(0).action());
        assertEquals(2_000L, entries.get(0).at());
        assertEquals("attack", entries.get(1).action());
        assertEquals(1_000L, entries.get(1).at());
        assertNull(entries.get(0).detail());
        assertEquals("-1", entries.get(1).detail());
    }

    @Test
    void salesAndRentalsRoundtrip() {
        UUID regionId = UUID.randomUUID();
        dao.saveSale(new RegionSale(regionId, UUID.randomUUID(), "Seller", 1500.0, 42L));
        dao.saveRental(new RegionRental(regionId, UUID.randomUUID(), "Owner", 100.0, 60,
                UUID.randomUUID(), "Tenant", 123_456L));

        Map<UUID, RegionSale> sales = dao.loadSales();
        assertEquals(1, sales.size());
        assertEquals(1500.0, sales.get(regionId).price(), 0.001);
        assertEquals("Seller", sales.get(regionId).sellerName());

        Map<UUID, RegionRental> rentals = dao.loadRentals();
        assertEquals(1, rentals.size());
        RegionRental rental = rentals.get(regionId);
        assertEquals(60, rental.durationMinutes());
        assertEquals(123_456L, rental.rentedUntil());
        assertEquals("Tenant", rental.tenantName());

        dao.deleteSale(regionId);
        dao.deleteRental(regionId);
        assertTrue(dao.loadSales().isEmpty());
        assertTrue(dao.loadRentals().isEmpty());
    }

    @Test
    void freshDatabaseHasCurrentSchemaVersion() {

        Map<UUID, Long> nothing = dao.loadLastSeen();
        assertTrue(nothing.isEmpty());

        assertTrue(dao.loadSales().isEmpty());
        assertTrue(dao.loadRentals().isEmpty());
    }
}
