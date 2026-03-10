package org.qweyns.pshologramm.features.autoadd;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.PSRegion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.qweyns.pshologramm.PSHologramm;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AutoAddManager implements Listener {
    private final PSHologramm plugin;

    private final Map<UUID, Set<String>> autoAddLists = new ConcurrentHashMap<>();
    private final Set<UUID> toggledOff = ConcurrentHashMap.newKeySet();

    public AutoAddManager(PSHologramm plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        for (Player p : Bukkit.getOnlinePlayers()) {
            loadPlayer(p.getUniqueId());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        saveAsync(uuid);
        autoAddLists.remove(uuid);
        toggledOff.remove(uuid);
    }

    private void loadPlayer(UUID uuid) {
        plugin.getStorageManager().loadAutoAddAsync(uuid, (friends, isToggledOff) -> {
            if (!friends.isEmpty()) autoAddLists.put(uuid, ConcurrentHashMap.newKeySet());
            if (!friends.isEmpty()) autoAddLists.get(uuid).addAll(friends);
            if (isToggledOff) toggledOff.add(uuid);
        });
    }

    private void saveAsync(UUID uuid) {
        Set<String> friends = autoAddLists.getOrDefault(uuid, new HashSet<>());
        boolean isToggledOff = toggledOff.contains(uuid);
        plugin.getStorageManager().saveAutoAddAsync(uuid, friends, isToggledOff);
    }

    public void toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (toggledOff.remove(uuid)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("autoadd_enabled"));
        } else {
            toggledOff.add(uuid);
            player.sendMessage(plugin.getLanguageManager().getMessage("autoadd_disabled"));
        }
        saveAsync(uuid);
    }

    public void addPlayer(Player owner, String target) {
        UUID uuid = owner.getUniqueId();
        autoAddLists.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(target.toLowerCase());
        owner.sendMessage(plugin.getLanguageManager().getMessage("autoadd_player_added", "%player%", target));
        saveAsync(uuid);
    }

    public void removePlayer(Player owner, String target) {
        UUID uuid = owner.getUniqueId();
        Set<String> list = autoAddLists.get(uuid);
        if (list != null && list.remove(target.toLowerCase())) {
            owner.sendMessage(plugin.getLanguageManager().getMessage("autoadd_player_removed", "%player%", target));
            saveAsync(uuid);
        } else {
            owner.sendMessage(plugin.getLanguageManager().getMessage("autoadd_player_not_found"));
        }
    }

    public void showList(Player owner) {
        Set<String> list = autoAddLists.getOrDefault(owner.getUniqueId(), new HashSet<>());
        owner.sendMessage(plugin.getLanguageManager().getMessage("autoadd_list_header"));
        if (list.isEmpty()) {
            owner.sendMessage(plugin.getLanguageManager().getMessage("autoadd_list_empty"));
        } else {
            owner.sendMessage(plugin.getLanguageManager().getMessage("autoadd_list_players", "%players%", String.join(", ", list)));
        }
    }

    public void applyToRegion(Player owner, PSRegion region) {
        if (toggledOff.contains(owner.getUniqueId())) return;
        Set<String> list = autoAddLists.get(owner.getUniqueId());
        if (list == null || list.isEmpty()) return;

        RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(region.getWorld()));
        if (rm != null) {
            ProtectedRegion pr = rm.getRegion(region.getId());
            if (pr != null) {
                for (String target : list) pr.getMembers().addPlayer(target);
                try { rm.saveChanges(); } catch (Exception ignored) {}
            }
        }
    }

    public void saveAllOnline() {
        for (UUID uuid : autoAddLists.keySet()) {
            Set<String> friends = autoAddLists.get(uuid);
            boolean isToggledOff = toggledOff.contains(uuid);
            plugin.getStorageManager().saveAutoAddSync(uuid, friends, isToggledOff);
        }
        for (UUID uuid : toggledOff) {
            if (!autoAddLists.containsKey(uuid)) {
                plugin.getStorageManager().saveAutoAddSync(uuid, new HashSet<>(), true);
            }
        }
    }
}
