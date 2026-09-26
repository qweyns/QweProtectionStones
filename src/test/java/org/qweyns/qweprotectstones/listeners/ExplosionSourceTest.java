package org.qweyns.qweprotectstones.listeners;

import org.bukkit.entity.Creeper;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExplosionSourceTest {

    @Test
    void mobsAndTheirProjectilesAreMobGriefing() {
        assertTrue(RegionExplosionListener.causedByMob(mock(Creeper.class)));

        Fireball ghastBall = mock(Fireball.class);
        when(ghastBall.getShooter()).thenReturn(mock(Ghast.class));
        assertTrue(RegionExplosionListener.causedByMob(ghastBall));
    }

    @Test
    void playerExplosivesAreNotMobGriefing() {
        assertFalse(RegionExplosionListener.causedByMob(mock(TNTPrimed.class)));

        Fireball playerBall = mock(Fireball.class);
        when(playerBall.getShooter()).thenReturn(mock(Player.class));
        assertFalse(RegionExplosionListener.causedByMob(playerBall));

        Fireball dispenserBall = mock(Fireball.class);
        when(dispenserBall.getShooter()).thenReturn(null);
        assertFalse(RegionExplosionListener.causedByMob(dispenserBall));
    }
}
