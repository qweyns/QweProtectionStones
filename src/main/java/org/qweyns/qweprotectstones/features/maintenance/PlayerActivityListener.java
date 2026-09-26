package org.qweyns.qweprotectstones.features.maintenance;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.qweyns.qweprotectstones.QweProtectStones;

public class PlayerActivityListener implements Listener {

    private final QweProtectStones plugin;

    public PlayerActivityListener(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        touch(event.getPlayer());
    }

    private void touch(Player player) {
        plugin.getRegionStorage().touchPlayerAsync(player.getUniqueId(), player.getName());
    }
}
