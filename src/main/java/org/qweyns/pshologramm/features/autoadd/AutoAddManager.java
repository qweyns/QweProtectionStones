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
import org.bukkit.event.player.PlayerQuitEvent;
import org.qweyns.pshologramm.PSHologramm;

import java.util.*;

public class AutoAddManager implements Listener {
    private final PSHologramm plugin;
    private final Map<UUID, Set<String>> autoAddLists = new HashMap<>();
    private final Set<UUID> toggledOff = new HashSet<>();

    public AutoAddManager(PSHologramm plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        autoAddLists.remove(event.getPlayer().getUniqueId());
        toggledOff.remove(event.getPlayer().getUniqueId());
    }

    public void toggle(Player player) {
        if (toggledOff.remove(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("autoadd_enabled"));
        } else {
            toggledOff.add(player.getUniqueId());
            player.sendMessage(plugin.getConfigManager().getMessage("autoadd_disabled"));
        }
    }

    public void addPlayer(Player owner, String target) {
        autoAddLists.computeIfAbsent(owner.getUniqueId(), k -> new HashSet<>()).add(target.toLowerCase());
        owner.sendMessage(plugin.getConfigManager().getMessage("autoadd_player_added", "%player%", target));
    }

    public void removePlayer(Player owner, String target) {
        Set<String> list = autoAddLists.get(owner.getUniqueId());
        if (list != null && list.remove(target.toLowerCase())) {
            owner.sendMessage(plugin.getConfigManager().getMessage("autoadd_player_removed", "%player%", target));
        } else {
            owner.sendMessage(plugin.getConfigManager().getMessage("autoadd_player_not_found"));
        }
    }

    public void showList(Player owner) {
        Set<String> list = autoAddLists.getOrDefault(owner.getUniqueId(), new HashSet<>());
        owner.sendMessage(plugin.getConfigManager().getMessage("autoadd_list_header"));
        if (list.isEmpty()) {
            owner.sendMessage(plugin.getConfigManager().getMessage("autoadd_list_empty"));
        } else {
            owner.sendMessage(plugin.getConfigManager().getMessage("autoadd_list_players", "%players%", String.join(", ", list)));
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
                for (String target : list) {
                    pr.getMembers().addPlayer(target);
                }
            }
        }
    }
}
