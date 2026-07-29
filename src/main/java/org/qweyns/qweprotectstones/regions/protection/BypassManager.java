package org.qweyns.qweprotectstones.regions.protection;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Режим обхода защиты для администрации. Отдельный переключатель нужен, чтобы
 * админ с правами не сносил чужие постройки по неосторожности: по умолчанию
 * режим выключен даже при наличии права.
 */
public class BypassManager implements Listener {

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean isEnabled(Player player) {
        return player != null && enabled.contains(player.getUniqueId());
    }

    /** @return новое состояние режима */
    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (enabled.remove(uuid)) return false;

        enabled.add(uuid);
        return true;
    }

    public void disable(Player player) {
        enabled.remove(player.getUniqueId());
    }

    /** Режим не переживает выход — иначе он тихо остаётся включённым навсегда. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        enabled.remove(event.getPlayer().getUniqueId());
    }

    public void clear() {
        enabled.clear();
    }
}
