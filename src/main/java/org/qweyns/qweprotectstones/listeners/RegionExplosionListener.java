package org.qweyns.qweprotectstones.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.api.SiegeService;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.event.RegionExplosionTypeEvent;

import java.util.List;

/** Защита списка блоков отдельно от побочных эффектов принятого взрыва. */
public class RegionExplosionListener implements Listener {
    public static final double MAX_DAMAGE_RADIUS = 256.0;
    private final QweProtectStones plugin;
    private final SiegeService siege;
    public RegionExplosionListener(QweProtectStones plugin, SiegeService siege) { this.plugin = plugin; this.siege = siege; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) { protect(event.blockList()); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) { protect(event.blockList()); }

    private void protect(List<Block> blocks) {
        blocks.removeIf(block -> {
            Region region = plugin.getRegionManager().getRegionAt(block.getLocation());
            return region != null && (region.isCore(block.getLocation())
                    || !plugin.getProtectionService().flag(region, RegionFlag.EXPLOSION_DAMAGE));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplodeApplied(BlockExplodeEvent event) {
        damage(event.getBlock().getLocation(), null, event.getBlock(), "BED", null);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplodeApplied(EntityExplodeEvent event) {
        String primer = event.getEntity() instanceof TNTPrimed primed
                && primed.getSource() instanceof Player player ? player.getName() : null;
        damage(event.getLocation(), event.getEntity(), null, classify(event.getEntityType()), primer);
    }

    private String classify(EntityType type) {
        return switch (type) {
            case WITHER, WITHER_SKULL -> "WITHER";
            case CREEPER -> "CREEPER";
            case END_CRYSTAL -> "ENDER_CRYSTAL";
            case WIND_CHARGE, BREEZE_WIND_CHARGE -> "WIND_CHARGE";
            default -> "TNT";
        };
    }

    private double baseRadius(RegionType type) {
        double value = type != null && type.overridesExplosionRadius() ? type.explosionDamageRadius()
                : plugin.getConfigManager().getExplosionDamageRadius();
        return Double.isFinite(value) ? Math.max(0, Math.min(MAX_DAMAGE_RADIUS, value)) : 0;
    }

    private void damage(Location center, Entity source, Block block, String defaultType, String primer) {
        if (center.getWorld() == null || !plugin.getConfigManager().isSiegeEnabled()) return;
        // Классификация нужна и вне границ привата: именно аддон расширяет область поиска.
        RegionExplosionTypeEvent event = new RegionExplosionTypeEvent(source, block, defaultType);
        Bukkit.getPluginManager().callEvent(event);
        double maximum = baseRadius(null);
        for (RegionType type : plugin.getRegionTypes().all()) maximum = Math.max(maximum, baseRadius(type));
        int radius = (int) Math.ceil(Math.min(MAX_DAMAGE_RADIUS, maximum * event.getDamageRadiusMultiplier()));
        RegionBounds bounds = RegionBounds.around(center.getBlockX(), center.getBlockY(), center.getBlockZ(),
                radius, radius, radius, center.getWorld().getMinHeight(), center.getWorld().getMaxHeight() - 1);
        for (Region region : plugin.getRegionManager().intersecting(center.getWorld().getName(), bounds)) {
            if (!siege.isDamaging(region, event.getExplosionType())) continue;
            Location core = region.getCoreLocation();
            double allowed = Math.min(MAX_DAMAGE_RADIUS,
                    baseRadius(plugin.getRegionTypes().byId(region.getTypeId())) * event.getDamageRadiusMultiplier());
            if (core == null || core.distanceSquared(center) > allowed * allowed) continue;
            if (plugin.getSchedulers().ownsLocation(core)) siege.damageRegion(region, 1, event.getExplosionType(), primer);
            else siege.damageRegionAsync(region, 1, event.getExplosionType(), primer).exceptionally(error -> {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Ошибка межрегиональной осады", error);
                return false;
            });
        }
    }
}
