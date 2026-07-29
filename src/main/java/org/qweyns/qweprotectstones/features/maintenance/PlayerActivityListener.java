package org.qweyns.qweprotectstones.features.maintenance;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.qweyns.qweprotectstones.QweProtectStones;

/**
 * Запоминает, когда игрок последний раз был на сервере. Без этих данных
 * невозможно отличить заброшенный приват от привата отпускника.
 */
public class PlayerActivityListener implements Listener {

    private final QweProtectStones plugin;

    public PlayerActivityListener(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        touch(event.getPlayer());
    }

    /** Отметка и на выходе: иначе долгая сессия выглядела бы как отсутствие. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        touch(event.getPlayer());
    }

    private void touch(Player player) {
        plugin.getRegionStorage().touchPlayerAsync(player.getUniqueId(), player.getName());
    }
}
