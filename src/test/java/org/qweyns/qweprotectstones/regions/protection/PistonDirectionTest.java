package org.qweyns.qweprotectstones.regions.protection;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PistonDirectionTest {
    @Test void retractDirectionAlreadyDescribesMovementInPaper() {
        QweProtectStones plugin = mock(QweProtectStones.class, RETURNS_DEEP_STUBS);
        World world = mock(World.class); when(world.getName()).thenReturn("world");
        Region region = new Region(UUID.randomUUID(),"world",new RegionBounds(-2,0,-2,2,10,2),0,5,0,
                "small",UUID.randomUUID(),"owner",5,10,1);
        Location from = new Location(world,0,5,0), to = new Location(world,-1,5,0), pistonAt = new Location(world,2,5,0);
        Block piston = mock(Block.class), core = mock(Block.class), target = mock(Block.class);
        when(piston.getLocation()).thenReturn(pistonAt);
        when(core.getLocation()).thenReturn(from);
        when(core.getRelative(BlockFace.WEST)).thenReturn(target);
        when(target.getLocation()).thenReturn(to);
        when(plugin.getProtectionService().regionAt(any(Location.class))).thenReturn(region);
        when(plugin.getRegionManager().getRegionAt(from)).thenReturn(region);
        when(plugin.getTunables().pistonsCanMoveCore()).thenReturn(true);
        BlockPistonRetractEvent event = mock(BlockPistonRetractEvent.class);
        when(event.getBlock()).thenReturn(piston); when(event.getBlocks()).thenReturn(List.of(core));
        when(event.getDirection()).thenReturn(BlockFace.WEST);
        BlockProtectionListener listener = new BlockProtectionListener(plugin);
        listener.onPistonRetract(event);
        verify(event, never()).setCancelled(true);
        assertNull(region.tryOperation());
        listener.onPistonRetractApplied(event);
        verify(plugin.getRegionManager()).updateCoreWithin(eq(region),eq(-1),eq(5),eq(0),any());
        try (var op = region.tryOperation()) { assertNotNull(op); }
    }
}
