package org.qweyns.qweprotectstones.api;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.*;
import org.qweyns.qweprotectstones.regions.event.*;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiegeTransactionTest {
    @Test void reentrantDamageAndDeleteVetoHaveNoExtraSideEffects() {
        QweProtectStones plugin = mock(QweProtectStones.class, RETURNS_DEEP_STUBS);
        RegionManager manager = new RegionManager(plugin);
        when(plugin.getRegionManager()).thenReturn(manager);
        Region region = new Region(UUID.randomUUID(), "world",new RegionBounds(-2,0,-2,2,10,2),0,5,0,
                "small", UUID.randomUUID(),"owner",5,10,1);
        manager.importRegion(region);
        RegionType type = mock(RegionType.class);
        when(plugin.getRegionTypes().all()).thenReturn(List.of(type));
        when(plugin.getRegionTypes().byId("small")).thenReturn(type);
        when(type.explosionDamages("TNT")).thenReturn(true);
        when(plugin.getConfigManager().isSiegeEnabled()).thenReturn(true);
        when(plugin.getSchedulers().ownsLocation(any())).thenReturn(true);
        when(plugin.getConfigManager().getDamageCooldownTicks()).thenReturn(20L);
        SiegeService siege = new SiegeService(plugin);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        PluginManager events = mock(PluginManager.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(Bukkit::getPluginManager).thenReturn(events);
            doAnswer(call -> { assertFalse(siege.damageRegion(region,1,"TNT","nested")); return null; })
                    .when(events).callEvent(any(RegionDamageEvent.class));
            doAnswer(call -> { ((RegionDeleteEvent) call.getArgument(0)).setCancelled(true); return null; })
                    .when(events).callEvent(any(RegionDeleteEvent.class));
            assertFalse(siege.damageRegion(region,5,"TNT","attacker"));
            assertEquals(5, region.getDurability());
            assertEquals(0, region.getAttackCount());
            assertEquals(0, siege.cooldownRemainingMs(region));
            verify(plugin.getPenaltyManager(), never()).markAttacked(any());
            assertTrue(siege.damageRegion(region,1,"TNT","attacker"));
            assertEquals(4, region.getDurability());
            assertEquals(1, region.getAttackCount());
            verify(plugin.getPenaltyManager()).markAttacked(region);
            assertFalse(siege.damageRegion(region,1,"TNT","attacker"));
            assertEquals(4, region.getDurability());
        }
    }
    @Test void radiusMultiplierIsFiniteAndBounded() {
        RegionExplosionTypeEvent event = new RegionExplosionTypeEvent(null,null,"TNT");
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 0})
            assertThrows(IllegalArgumentException.class, () -> event.setDamageRadiusMultiplier(invalid));
        event.setDamageRadiusMultiplier(10);
        assertEquals(10,event.getDamageRadiusMultiplier());
        event.setDamageRadiusMultiplier(10000);
        assertEquals(100,event.getDamageRadiusMultiplier());
    }
}
