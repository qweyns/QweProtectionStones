package org.qweyns.qweprotectstones.regions.protection;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BypassManager implements Listener {

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean isEnabled(Player player) {
        return player != null && enabled.contains(player.getUniqueId());
    }

    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (enabled.remove(uuid)) return false;

        enabled.add(uuid);
        return true;
    }

    public void disable(Player player) {
        enabled.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        enabled.remove(event.getPlayer().getUniqueId());
    }

    public void clear() {
        enabled.clear();
    }
}
