package org.qweyns.qweprotectstones.regions.protection;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.entity.PotionSplashEvent;
import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionFlag;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProtectionRegressionTest {
    @Test
    void splashProtectionUpdatesEventInsteadOfItsSnapshot() {
        QweProtectStones plugin = mock(QweProtectStones.class);
        ProtectionService protection = mock(ProtectionService.class);
        when(plugin.getProtectionService()).thenReturn(protection);
        Player thrower = mock(Player.class);
        Player target = mock(Player.class);
        Location location = mock(Location.class);
        when(target.getLocation()).thenReturn(location);
        Region region = mock(Region.class);
        when(protection.regionAt(location)).thenReturn(region);
        when(protection.flag(region, RegionFlag.PVP)).thenReturn(false);
        ThrownPotion potion = mock(ThrownPotion.class);
        when(potion.getShooter()).thenReturn(thrower);
        PotionSplashEvent event = mock(PotionSplashEvent.class);
        when(event.getPotion()).thenReturn(potion);
        // Неизменяемый снимок намеренно: removeIf не является контрактом события.
        when(event.getAffectedEntities()).thenReturn(List.of(thrower, target));
        new EntityProtectionListener(plugin).onPotionSplash(event);
        verify(event).setIntensity(target, 0.0);
        verify(event, never()).setIntensity(eq(thrower), anyDouble());
    }

    @Test
    void iceFlagDoesNotControlUnrelatedBlockTransformations() {
        assertTrue(BlockProtectionListener.isIceOrSnow(Material.ICE));
        assertTrue(BlockProtectionListener.isIceOrSnow(Material.FROSTED_ICE));
        assertTrue(BlockProtectionListener.isIceOrSnow(Material.SNOW));
        assertTrue(BlockProtectionListener.isIceOrSnow(Material.SNOW_BLOCK));
        for (Material material : List.of(Material.STONE, Material.COBBLESTONE,
                Material.OBSIDIAN, Material.FIRE, Material.FARMLAND)) {
            assertFalse(BlockProtectionListener.isIceOrSnow(material));
        }
    }
}
