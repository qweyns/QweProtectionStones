package org.qweyns.qweprotectstones.features.autoadd;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.QweProtectStones;
import java.util.*;
import java.util.function.BiConsumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutoAddSessionTest {
    @Test void quitBeforeLoadDoesNotSaveEmptyDataAndOldSessionCannotOverwriteNew() {
        QweProtectStones plugin = mock(QweProtectStones.class, RETURNS_DEEP_STUBS);
        Player player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        PlayerJoinEvent join = mock(PlayerJoinEvent.class); when(join.getPlayer()).thenReturn(player);
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class); when(quit.getPlayer()).thenReturn(player);
        List<BiConsumer<Set<String>,Boolean>> callbacks = new ArrayList<>();
        var storage = plugin.getRegionStorage();
        doAnswer(call -> { callbacks.add(call.getArgument(1)); return null; })
                .when(storage).loadAutoAddAsync(any(),any(),any());
        var schedulers = plugin.getSchedulers();
        doAnswer(call -> { ((Runnable)call.getArgument(1)).run(); return null; }).when(schedulers).runAtEntity(eq(player),any());
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            AutoAddManager manager = new AutoAddManager(plugin);
            manager.onJoin(join);
            manager.toggle(player); // Не загружено — не меняем и не сохраняем.
            manager.onQuit(quit);
            verify(storage, never()).saveAutoAddAsync(any(),any(),anyBoolean());
            manager.onJoin(join);
            callbacks.get(1).accept(Set.of("new"),false);
            callbacks.get(0).accept(Set.of("old"),true);
            assertEquals(List.of("new"), manager.getList(player));
            manager.onQuit(quit);
            verify(storage).saveAutoAddAsync(player.getUniqueId(),Set.of("new"),false);
        }
    }
}
