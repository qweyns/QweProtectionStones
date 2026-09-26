package org.qweyns.qweprotectstones.regions;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.event.RegionCreateEvent;
import org.qweyns.qweprotectstones.regions.event.RegionDeleteEvent;
import org.qweyns.qweprotectstones.storage.RegionStorage;

import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegionReservationTest {
    @Test void cancelledNativePlacementReleasesReservationWithoutPersisting() {
        QweProtectStones plugin = mock(QweProtectStones.class);
        RegionStorage storage = mock(RegionStorage.class);
        when(plugin.getRegionStorage()).thenReturn(storage);
        RegionManager manager = new RegionManager(plugin);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        Player owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(owner.getName()).thenReturn("owner");
        when(owner.hasPermission(anyString())).thenReturn(true);
        when(owner.getEffectivePermissions()).thenReturn(Set.of());
        RegionType type = mock(RegionType.class);
        when(type.id()).thenReturn("small");
        when(type.placePermission()).thenReturn("");
        when(type.isWorldAllowed("world")).thenReturn(true);
        when(type.radiusX()).thenReturn(2); when(type.radiusZ()).thenReturn(2);
        when(type.maxDurability()).thenReturn(10);
        Location core = new Location(world, 0, 64, 0);
        PluginManager events = mock(PluginManager.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(events);
            var prepared = manager.prepareCreation(owner, type, core);
            assertTrue(prepared.successful());
            assertEquals(0, manager.size());
            verifyNoInteractions(storage);
            assertEquals(RegionManager.CreateStatus.OVERLAP, manager.prepareCreation(owner, type, core).status());
            manager.abortCreation(prepared.region());
            var accepted = manager.prepareCreation(owner, type, core);
            assertTrue(accepted.successful());
            manager.commitCreation(accepted.region());
            manager.commitCreation(accepted.region());
            assertEquals(1, manager.size());
            verify(storage, times(1)).save(accepted.region());
        }
    }
    @Test void cancelledBreakDoesNotDeleteAndVetoReleasesReservation() {
        QweProtectStones plugin = mock(QweProtectStones.class);
        RegionStorage storage = mock(RegionStorage.class);
        when(plugin.getRegionStorage()).thenReturn(storage);
        RegionManager manager = new RegionManager(plugin);
        Region region = RegionMutationTest.region();
        assertTrue(manager.importRegion(region));
        PluginManager events = mock(PluginManager.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(events);
            var prepared = manager.prepareDeletion(region, RegionDeleteEvent.Reason.BROKEN, null);
            assertNotNull(prepared);
            assertSame(region, manager.getById(region.getId()));
            assertNull(region.tryOperation());
            prepared.close(); // Нативное событие отменено — commit не вызывается.
            verify(storage, never()).delete(any());
            doAnswer(call -> { ((RegionDeleteEvent) call.getArgument(0)).setCancelled(true); return null; })
                    .when(events).callEvent(any(RegionDeleteEvent.class));
            assertFalse(manager.deleteRegion(region, RegionDeleteEvent.Reason.BROKEN, null));
            assertSame(region, manager.getById(region.getId()));
            try (var op = region.tryOperation()) { assertNotNull(op); }
        }
    }
    @Test void chunkViewCannotBeUsedToMutateIndex() {
        // Проверка мутаций индекса во время callback вынесена в сценарий резервации выше;
        // здесь проверяем неизменяемость выдаваемого набора чанка.
        RegionIndex<Region> index = new RegionIndex<>();
        Region region = RegionMutationTest.region(); index.add(region);
        assertThrows(UnsupportedOperationException.class, () -> index.inChunk("world", 0, 0).clear());
        assertSame(region, index.at("world", 0, 5, 0));
    }
}
