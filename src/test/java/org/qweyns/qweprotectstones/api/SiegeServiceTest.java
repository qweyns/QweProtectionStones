package org.qweyns.qweprotectstones.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.config.ConfigManager;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionManager;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.RegionTypeRegistry;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiegeServiceTest {
    private ConfigManager config;
    private Region region;
    private RegionType type;
    private RegionManager manager;
    private SiegeService siege;

    @BeforeEach
    void setup() {
        QweProtectStones plugin = mock(QweProtectStones.class);
        config = mock(ConfigManager.class);
        RegionTypeRegistry types = mock(RegionTypeRegistry.class);
        manager = mock(RegionManager.class);
        when(plugin.getConfigManager()).thenReturn(config);
        when(plugin.getRegionTypes()).thenReturn(types);
        when(plugin.getRegionManager()).thenReturn(manager);
        type = mock(RegionType.class);
        when(types.all()).thenReturn(List.of(type));
        region = mock(Region.class);
        when(region.getId()).thenReturn(UUID.randomUUID());
        when(region.getTypeId()).thenReturn("test");
        when(types.byId("test")).thenReturn(type);
        when(manager.getById(region.getId())).thenReturn(region);
        when(config.isSiegeEnabled()).thenReturn(true);
        when(type.explosionDamages("TNT")).thenReturn(true);
        siege = new SiegeService(plugin);
    }

    @Test void disabledSiegeRejectsApiDamage() {
        when(config.isSiegeEnabled()).thenReturn(false);
        assertFalse(siege.damageRegion(region, 1, "TNT", null));
        verify(region, never()).getCoreLocation();
    }
    @Test void disabledExplosionTypeRejectsApiDamage() {
        assertFalse(siege.damageRegion(region, 1, "CUSTOM", null));
        verify(region, never()).getCoreLocation();
    }
    @Test void removedRegionRejectsApiDamage() {
        when(manager.getById(region.getId())).thenReturn(null);
        assertFalse(siege.damageRegion(region, 1, "TNT", null));
        verify(region, never()).getCoreLocation();
    }
    @Test void unloadedWorldDoesNotConsumeCooldown() {
        when(config.getDamageCooldownTicks()).thenReturn(100L);
        assertFalse(siege.damageRegion(region, 1, "TNT", null));
        assertEquals(0, siege.cooldownRemainingMs(region));
    }
}
