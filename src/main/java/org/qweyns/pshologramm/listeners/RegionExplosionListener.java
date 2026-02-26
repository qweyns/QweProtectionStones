package org.qweyns.pshologramm.listeners;

import dev.espi.protectionstones.PSRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.models.RegionData;

import java.util.*;

public class RegionExplosionListener implements Listener {
    private final PSHologramm plugin;
    private final Set<String> processingRemoval = new HashSet<>();
    private final Map<String, Long> lastDamageTime = new HashMap<>();

    public RegionExplosionListener(PSHologramm plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Location center = event.getBlock().getLocation();
        if (center.getWorld() == null) return;

        Set<Location> nearbyCores = new HashSet<>();
        for (RegionData rd : plugin.getStorageManager().getAllRegions().values()) {
            if (rd.getWorld() != null && rd.getWorld().equals(center.getWorld().getName())) {
                Location coreLoc = new Location(center.getWorld(), rd.getX(), rd.getY(), rd.getZ());
                if (coreLoc.distanceSquared(center) <= 900) {
                    nearbyCores.add(coreLoc);
                }
            }
        }

        event.blockList().removeIf(b -> nearbyCores.contains(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        EntityType type = event.getEntityType();
        Location center = event.getLocation();
        if (center.getWorld() == null) return;

        boolean isDamagingExplosion = (type == EntityType.TNT || type == EntityType.TNT_MINECART || type == EntityType.WITHER_SKULL || type == EntityType.WITHER);

        Set<PSRegion> damagedRegions = new HashSet<>();
        Set<PSRegion> penalizedRegions = new HashSet<>();
        Map<Location, String> nearbyCores = new HashMap<>();

        for (RegionData rd : plugin.getStorageManager().getAllRegions().values()) {
            if (rd.getWorld() != null && rd.getWorld().equals(center.getWorld().getName())) {
                Location coreLoc = new Location(center.getWorld(), rd.getX(), rd.getY(), rd.getZ());
                if (coreLoc.distanceSquared(center) <= 900) {
                    nearbyCores.put(coreLoc, rd.getId());
                }
            }
        }

        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (nearbyCores.containsKey(b.getLocation())) {
                it.remove();
                if (isDamagingExplosion) {
                    PSRegion reg = PSRegion.fromLocation(b.getLocation());
                    if (reg != null) { damagedRegions.add(reg); penalizedRegions.add(reg); }
                }
            }
        }

        if (isDamagingExplosion) {
            int radius = plugin.getConfigManager().getExplosionPenaltyRadius();
            double radiusSq = radius * radius;
            Location centerBlock = center.getBlock().getLocation();

            for (Map.Entry<Location, String> entry : nearbyCores.entrySet()) {
                Location coreLoc = entry.getKey();
                PSRegion reg = null;

                if (coreLoc.distanceSquared(centerBlock) <= radiusSq) {
                    reg = PSRegion.fromLocation(coreLoc);
                    if (reg != null) penalizedRegions.add(reg);
                    continue;
                }
                for (Block b : event.blockList()) {
                    if (coreLoc.distanceSquared(b.getLocation()) <= radiusSq) {
                        if (reg == null) reg = PSRegion.fromLocation(coreLoc);
                        if (reg != null) penalizedRegions.add(reg);
                        break;
                    }
                }
            }
        }

        for (PSRegion region : penalizedRegions) plugin.getPenaltyManager().markAttacked(region.getId());

        for (PSRegion region : damagedRegions) {
            String expType = (type == EntityType.TNT || type == EntityType.TNT_MINECART) ? "TNT" : "WITHER";
            if (plugin.getConfigManager().isExplosionAllowed(region.getType(), expType)) {
                damageRegionByExplosion(region);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWitherBreak(EntityChangeBlockEvent event) {
        if (event.getEntityType() == EntityType.WITHER) {
            PSRegion reg = PSRegion.fromLocation(event.getBlock().getLocation());
            if (reg != null) {
                Location coreLoc = reg.getProtectBlock().getLocation();
                Location breakLoc = event.getBlock().getLocation();

                if (coreLoc.equals(breakLoc)) {
                    event.setCancelled(true);
                    plugin.getPenaltyManager().markAttacked(reg.getId());
                    if (plugin.getConfigManager().isExplosionAllowed(reg.getType(), "WITHER")) {
                        damageRegionByExplosion(reg);
                    }
                } else {
                    int radius = plugin.getConfigManager().getExplosionPenaltyRadius();
                    if (coreLoc.distanceSquared(breakLoc) <= (radius * radius)) {
                        plugin.getPenaltyManager().markAttacked(reg.getId());
                    }
                }
            }
        }
    }

    private void damageRegionByExplosion(PSRegion region) {
        String id = region.getId();
        if (processingRemoval.contains(id)) return;

        long cooldownMs = plugin.getConfigManager().getDamageCooldownTicks() * 50L;
        long now = System.currentTimeMillis();
        if (now - lastDamageTime.getOrDefault(id, 0L) < cooldownMs) return;
        lastDamageTime.put(id, now);

        RegionData rd = plugin.getStorageManager().getRegion(id);
        if (rd == null) return;

        int dur = rd.getDurability();
        int maxDur = rd.getMaxDurability();
        Location loc = region.getProtectBlock().getLocation();
        String rType = region.getType();
        String owner = rd.getOwner() != null ? rd.getOwner() : plugin.getConfigManager().getRawMessage("unknown_owner");

        plugin.getNotificationManager().sendAttackAlert(id, owner, rType, loc);
        plugin.getVisualManager().spawnDamageIndicator(loc);

        if (dur > 1) {
            rd.setDurability(dur - 1);
            plugin.getStorageManager().saveRegion(rd);
            plugin.getVisualManager().playEffect(loc, rType, "damage");
            plugin.getHologramManager().createOrUpdateHologram(id, loc, rType, owner, rd.getDurability(), maxDur);
        } else {
            processingRemoval.add(id);
            plugin.getHologramManager().removeHologram(id);
            plugin.getVisualManager().playRedstoneBoundary(region, "remove");
            plugin.getVisualManager().removeGlow(id);
            plugin.getVisualManager().playEffect(loc, rType, "remove");

            plugin.getStorageManager().removeRegion(id);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                loc.getBlock().setType(Material.AIR);
                region.deleteRegion(false);
                processingRemoval.remove(id);
            }, 1L);
        }
    }
}
