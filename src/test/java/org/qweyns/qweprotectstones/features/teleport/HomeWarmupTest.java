package org.qweyns.qweprotectstones.features.teleport;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.config.ConfigManager;
import org.qweyns.qweprotectstones.config.LanguageManager;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionManager;
import org.qweyns.qweprotectstones.regions.TrustLevel;
import org.qweyns.qweprotectstones.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HomeWarmupTest {
    private HomeWarmup warmup;
    private Player player;
    private Region region;
    private RegionManager manager;
    private Schedulers.Task task;
    private final List<Runnable> timers = new ArrayList<>();
    private final List<Runnable> entityCallbacks = new ArrayList<>();
    private final Location home = new Location(null, 0, 64, 0);

    @BeforeEach void setup() {
        QweProtectStones plugin = mock(QweProtectStones.class);
        ConfigManager config = mock(ConfigManager.class);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("home.warmup-seconds", 2);
        when(config.getConfig()).thenReturn(yaml);
        when(plugin.getConfigManager()).thenReturn(config);
        LanguageManager language = mock(LanguageManager.class);
        when(plugin.getLanguageManager()).thenReturn(language);
        // Содержимое сообщений не относится к проверке владения потоком.
        when(language.getMessage(anyString(), any(String[].class))).thenReturn(Component.empty());
        Schedulers schedulers = mock(Schedulers.class);
        when(plugin.getSchedulers()).thenReturn(schedulers);
        task = mock(Schedulers.Task.class);
        when(schedulers.runLater(any(Runnable.class), anyLong())).thenAnswer(call -> {
            timers.add(call.getArgument(0));
            return task;
        });
        doAnswer(call -> { entityCallbacks.add(call.getArgument(1)); return null; })
                .when(schedulers).runAtEntity(any(), any());
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(null, 0, 0, 0, 90, 30));
        when(player.teleportAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(true));
        region = mock(Region.class);
        when(region.getId()).thenReturn(UUID.randomUUID());
        when(region.getShortId()).thenReturn("test");
        when(region.getTrust(player.getUniqueId())).thenReturn(TrustLevel.parse("build").orElseThrow());
        manager = mock(RegionManager.class);
        when(plugin.getRegionManager()).thenReturn(manager);
        when(manager.getById(region.getId())).thenReturn(region);
        warmup = new HomeWarmup(plugin);
    }

    @Test void globalTimerOnlySchedulesPlayerWork() {
        assertTrue(warmup.teleport(player, region, home));
        timers.getFirst().run();
        verify(player, never()).getLocation();
        verify(player, never()).teleportAsync(any(Location.class));
        entityCallbacks.getFirst().run();
        verify(player).teleportAsync(any(Location.class));
        assertEquals(0, home.getYaw(), "Исходная точка не должна меняться");
    }

    @Test void removedRegionIsRejectedAfterWarmup() {
        warmup.teleport(player, region, home);
        when(manager.getById(region.getId())).thenReturn(null);
        timers.getFirst().run();
        entityCallbacks.getFirst().run();
        verify(player, never()).teleportAsync(any(Location.class));
    }

    @Test void staleCallbackDoesNotFinishNewWarmup() {
        warmup.teleport(player, region, home);
        timers.getFirst().run();
        warmup.teleport(player, region, home);
        entityCallbacks.getFirst().run();
        verify(player, never()).teleportAsync(any(Location.class));
        timers.get(1).run();
        entityCallbacks.get(1).run();
        verify(player).teleportAsync(any(Location.class));
    }

    @Test void quitCancelsTask() {
        warmup.teleport(player, region, home);
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);
        warmup.onPlayerQuit(event);
        verify(task).cancel();
        timers.getFirst().run();
        entityCallbacks.getFirst().run();
        verify(player, never()).teleportAsync(any(Location.class));
    }
}
