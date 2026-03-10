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

    public RegionExplosionListener(PSHologramm plugin) { this.plugin = plugin; }

    private Map<Location, String> getNearbyCores(Location center, int radius) {
        Map<Location, String> cores = new HashMap<>();
        if (center.getWorld() == null) return cores;

        String world = center.getWorld().getName();
        int minCX = (center.getBlockX() - radius) >> 4;
        int maxCX = (center.getBlockX() + radius) >> 4;
        int minCZ = (center.getBlockZ() - radius) >> 4;
        int maxCZ = (center.getBlockZ() + radius) >> 4;
        double radiusSq = radius * radius;

        for (int x = minCX; x <= maxCX; x++) {
            for (int z = minCZ; z <= maxCZ; z++) {
                for (RegionData rd : plugin.getStorageManager().getRegionsInChunk(world, x, z)) {
                    Location coreLoc = new Location(center.getWorld(), rd.getX(), rd.getY(), rd.getZ());
                    if (coreLoc.distanceSquared(center) <= radiusSq) {
                        cores.put(coreLoc, rd.getId());
                    }
                }
            }
        }
        return cores;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        String expType = "BED";
        handleExplosion(event.getBlock().getLocation(), event.blockList(), expType);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        String expType = "TNT";

        if (event.getEntityType() == EntityType.WITHER || event.getEntityType() == EntityType.WITHER_SKULL) {
            expType = "WITHER";
        } else if (event.getEntityType() == EntityType.CREEPER) {
            expType = "CREEPER";
        } else if (event.getEntityType() == EntityType.END_CRYSTAL) {
            expType = "ENDER_CRYSTAL";
        }

        handleExplosion(event.getLocation(), event.blockList(), expType);
    }

    private void handleExplosion(Location center, List<Block> blockList, String expType) {
        if (center.getWorld() == null) return;

        Set<PSRegion> damagedRegions = new HashSet<>();
        Set<PSRegion> penalizedRegions = new HashSet<>();

        Map<Location, String> nearbyCores = getNearbyCores(center, 30);

        blockList.removeIf(b -> nearbyCores.containsKey(b.getLocation()));

        double damageRadiusSq = 16.0;

        int penaltyRadius = plugin.getConfigManager().getExplosionPenaltyRadius();
        double penaltyRadiusSq = penaltyRadius * penaltyRadius;

        for (Map.Entry<Location, String> entry : nearbyCores.entrySet()) {
            Location coreLoc = entry.getKey();
            double distSq = coreLoc.distanceSquared(center);

            if (distSq <= penaltyRadiusSq) {
                PSRegion reg = PSRegion.fromLocation(coreLoc);
                if (reg != null) {
                    penalizedRegions.add(reg);

                    if (distSq <= damageRadiusSq) {
                        damagedRegions.add(reg);
                    }
                }
            }
        }

        for (PSRegion region : penalizedRegions) {
            plugin.getPenaltyManager().markAttacked(region.getId());
        }
        for (PSRegion region : damagedRegions) {
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
                if (coreLoc.equals(event.getBlock().getLocation())) {
                    event.setCancelled(true);
                    plugin.getPenaltyManager().markAttacked(reg.getId());
                    if (plugin.getConfigManager().isExplosionAllowed(reg.getType(), "WITHER")) damageRegionByExplosion(reg);
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
        Location loc = region.getProtectBlock().getLocation();
        String owner = (rd.getOwner() != null && !rd.getOwner().isEmpty()) ? rd.getOwner() : plugin.getLanguageManager().getRawMessage("unknown_owner");

        plugin.getNotificationManager().sendAttackAlert(id, owner, region.getType(), loc);
        plugin.getVisualManager().spawnDamageIndicator(loc);

        if (dur > 1) {
            rd.setDurability(dur - 1);
            plugin.getStorageManager().saveRegion(rd);
            plugin.getVisualManager().playEffect(loc, region.getType(), "damage");
            plugin.getHologramManager().createOrUpdateHologram(id, loc, region.getType(), owner, rd.getDurability(), rd.getMaxDurability());
        } else {
            processingRemoval.add(id);
            plugin.getHologramManager().removeHologram(id);
            plugin.getVisualManager().playRedstoneBoundary(region, "remove");
            plugin.getVisualManager().removeGlow(id);
            plugin.getVisualManager().playEffect(loc, region.getType(), "remove");
            plugin.getStorageManager().removeRegion(id);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                loc.getBlock().setType(Material.AIR);
                region.deleteRegion(false);
                processingRemoval.remove(id);
            }, 1L);
        }
    }
}
