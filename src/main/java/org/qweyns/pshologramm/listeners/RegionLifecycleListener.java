package org.qweyns.pshologramm.listeners;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.event.PSCreateEvent;
import dev.espi.protectionstones.event.PSRemoveEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.models.RegionData;

import java.util.ArrayList;

public class RegionLifecycleListener implements Listener {
    private final PSHologramm plugin;

    public RegionLifecycleListener(PSHologramm plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        plugin.getHologramManager().restoreHolograms();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPSRemove(PSRemoveEvent event) {
        String id = event.getRegion().getId();

        plugin.getHologramManager().removeHologram(id);
        plugin.getVisualManager().playRedstoneBoundary(event.getRegion(), "remove");
        plugin.getVisualManager().removeGlow(id);
        plugin.getVisualManager().playEffect(event.getRegion().getProtectBlock().getLocation(), event.getRegion().getType(), "remove");

        plugin.getPenaltyManager().removeRegion(id);
        plugin.getStorageManager().removeRegion(id);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPSCreate(PSCreateEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        String type = event.getRegion().getType();
        Material mat = event.getRegion().getProtectBlock().getType();
        Location loc = event.getRegion().getProtectBlock().getLocation();
        String id = event.getRegion().getId();

        if (!plugin.getConfigManager().isMergeEnabled()) {
            RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(event.getRegion().getWorld()));
            if (rm != null) {
                ApplicableRegionSet set = rm.getApplicableRegions(event.getRegion().getWGRegion());
                for (ProtectedRegion r : set) {
                    if (!r.getId().equals(id) && r.getOwners().contains(player.getUniqueId())) {
                        event.setCancelled(true);
                        player.sendMessage(plugin.getConfigManager().getMessage("merge_disabled"));
                        return;
                    }
                }
            }
        }

        int dur = plugin.getConfigManager().getStartDurability(type);
        int maxDur = plugin.getConfigManager().getMaxDurability(type);

        plugin.getVisualManager().playEffect(loc, type, "create");
        plugin.getVisualManager().playRedstoneBoundary(event.getRegion(), "create");
        plugin.getHologramManager().createOrUpdateHologram(id, loc, type, player.getName(), dur, maxDur);
        plugin.getAutoAddManager().applyToRegion(player, event.getRegion());

        RegionData rd = new RegionData(
                id, type, player.getName(), mat.name(), dur, maxDur,
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), new ArrayList<>()
        );
        plugin.getStorageManager().saveRegion(rd);
    }
}
