package org.qweyns.qweprotectstones.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.Mockito.*;

class BukkitSchedulersTest {
    @Test void entityWorkIsDeferredAndDoesNotReadEntityFromCallingThread() {
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        Entity entity = mock(Entity.class);
        Runnable action = mock(Runnable.class), retired = mock(Runnable.class);
        AtomicReference<Runnable> task = new AtomicReference<>();
        when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(call -> {
            task.set(call.getArgument(1)); return mock(BukkitTask.class);
        });
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            Schedulers bridge = new BukkitSchedulers(plugin);
            bridge.runAtEntity(entity,action,retired);
            verifyNoInteractions(entity,action,retired);
            when(entity.isValid()).thenReturn(true);
            task.get().run();
            verify(action).run(); verifyNoInteractions(retired);
            bridge.runAtEntity(entity,action,retired);
            when(entity.isValid()).thenReturn(false);
            task.get().run();
            verify(action,times(1)).run(); verify(retired).run();
            verify(entity,never()).getLocation();
        }
    }
}
