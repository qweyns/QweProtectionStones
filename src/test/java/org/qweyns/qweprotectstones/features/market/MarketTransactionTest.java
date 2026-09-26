package org.qweyns.qweprotectstones.features.market;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.*;
import org.qweyns.qweprotectstones.regions.event.RegionMemberChangeEvent;

import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketTransactionTest {
    private QweProtectStones plugin;
    private MarketManager market;
    private RegionManager manager;
    private Region region;
    private Player owner, tenant;
    @BeforeEach void setup() {
        plugin = mock(QweProtectStones.class, RETURNS_DEEP_STUBS);
        manager = new RegionManager(plugin);
        when(plugin.getRegionManager()).thenReturn(manager);
        market = new MarketManager(plugin);
        when(plugin.getMarketManager()).thenReturn(market);
        owner = player("owner"); tenant = player("tenant");
        region = new Region(UUID.randomUUID(), "world", new RegionBounds(-2,0,-2,2,10,2), 0,5,0,
                "small", owner.getUniqueId(), "owner", 5,10,1);
        manager.importRegion(region);
        when(plugin.getConfigManager().getConfig().getString("market.rent.trust-level", "container")).thenReturn("container");
        when(plugin.getVaultHook().takeMoney(any(), anyDouble())).thenReturn(true);
        when(plugin.getVaultHook().giveMoney(any(), anyDouble())).thenReturn(true);
    }
    private Player player(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.getName()).thenReturn(name);
        return player;
    }
    @Test void activeRentalCannotBeOverwritten() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(() -> Bukkit.getOfflinePlayer(owner.getUniqueId())).thenReturn(mock(OfflinePlayer.class));
            assertTrue(market.offerForRent(region, owner, 100, 10));
            assertTrue(market.takeRent(tenant, region));
            RegionRental active = market.getRental(region);
            assertFalse(market.offerForRent(region, owner, 200, 20));
            assertSame(active, market.getRental(region));
            assertTrue(region.getMember(tenant.getUniqueId()).isPresent());
        }
    }
    @Test void expiredTenantRemovedBeforeNewTake() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(() -> Bukkit.getOfflinePlayer(owner.getUniqueId())).thenReturn(mock(OfflinePlayer.class));
            UUID previous = UUID.randomUUID();
            region.setMember(previous, "old", TrustLevel.parse("container").orElseThrow());
            var field = MarketManager.class.getDeclaredField("rentals"); field.setAccessible(true);
            @SuppressWarnings("unchecked") Map<UUID, RegionRental> rentals = (Map<UUID, RegionRental>) field.get(market);
            rentals.put(region.getId(), new RegionRental(region.getId(), owner.getUniqueId(), "owner",100,10,previous,"old",1));
            assertTrue(market.takeRent(tenant, region));
            assertTrue(region.getMember(previous).isEmpty());
            assertEquals(tenant.getUniqueId(), market.getRental(region).tenantId());
        }
    }
    @Test void vetoDoesNotDropRentalRecordOrTenant() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            PluginManager events = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(events);
            bukkit.when(() -> Bukkit.getOfflinePlayer(owner.getUniqueId())).thenReturn(mock(OfflinePlayer.class));
            assertTrue(market.offerForRent(region, owner, 100, 10));
            assertTrue(market.takeRent(tenant, region));
            doAnswer(call -> { ((RegionMemberChangeEvent) call.getArgument(0)).setCancelled(true); return null; })
                    .when(events).callEvent(any(RegionMemberChangeEvent.class));
            assertFalse(market.cancelRental(region));
            assertNotNull(market.getRental(region));
            assertTrue(region.getMember(tenant.getUniqueId()).isPresent());
        }
    }
    @Test void apiTransferRemovesOldSaleAndRebindsRental() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            assertTrue(market.listForSale(region, owner, 100));
            assertTrue(market.offerForRent(region, owner, 50, 10));
            manager.transferRegion(region, tenant.getUniqueId(), "tenant");
            assertNull(market.getSale(region));
            assertEquals(tenant.getUniqueId(), market.getRental(region).ownerId());
        }
    }
    @Test void paymentCallbackCannotDeleteOrBuyAgain() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(() -> Bukkit.getOfflinePlayer(owner.getUniqueId())).thenReturn(mock(OfflinePlayer.class));
            assertTrue(market.listForSale(region, owner, 100));
            when(plugin.getVaultHook().takeMoney(tenant, 100)).thenAnswer(call -> {
                assertFalse(market.buy(tenant, region));
                assertFalse(manager.deleteRegion(region, org.qweyns.qweprotectstones.regions.event.RegionDeleteEvent.Reason.COMMAND, null));
                return true;
            });
            assertTrue(market.buy(tenant, region));
            assertTrue(region.isOwner(tenant.getUniqueId()));
            verify(plugin.getVaultHook(), times(1)).takeMoney(tenant, 100);
        }
    }
}
