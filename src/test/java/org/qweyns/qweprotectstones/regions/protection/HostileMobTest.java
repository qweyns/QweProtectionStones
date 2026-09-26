package org.qweyns.qweprotectstones.regions.protection;

import org.bukkit.entity.Cow;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HostileMobTest {

    @Test
    void enemiesOutsideMonsterInterfaceAreHostile() {
        assertTrue(EntityProtectionListener.hostile(mock(Zombie.class)));
        assertTrue(EntityProtectionListener.hostile(mock(Slime.class)));
        assertTrue(EntityProtectionListener.hostile(mock(Phantom.class)));
        assertFalse(EntityProtectionListener.hostile(mock(Cow.class)));
    }
}
