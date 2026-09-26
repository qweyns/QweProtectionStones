package org.qweyns.qweprotectstones.holograms;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.Mockito.*;

class NativeHologramLifecycleTest {
    @Test void deletedRegionOrCancelledRequestCannotSpawnLateHologram() {
        QweProtectStones plugin = mock(QweProtectStones.class, RETURNS_DEEP_STUBS);
        when(plugin.isEnabled()).thenReturn(true);
        Region region = mock(Region.class); when(region.getId()).thenReturn(UUID.randomUUID());
        when(region.getTypeId()).thenReturn("small");
        World world = mock(World.class);
        Location core = new Location(world,0,64,0);
        AtomicReference<Runnable> callback = new AtomicReference<>();
        var schedulers = plugin.getSchedulers();
        doAnswer(call -> { callback.set(call.getArgument(1)); return null; }).when(schedulers).runAtLocation(any(),any());
        NativeHologramProvider provider = new NativeHologramProvider(plugin);
        provider.createOrUpdate(region,core);
        callback.get().run(); // getById != region — удалён.
        verifyNoInteractions(world);
        when(plugin.getRegionManager().getById(region.getId())).thenReturn(region);
        provider.createOrUpdate(region,core);
        provider.remove(region.getId());
        callback.get().run(); // remove инвалидирует запрос, хотя регион ещё жив.
        verifyNoInteractions(world);
    }
}
