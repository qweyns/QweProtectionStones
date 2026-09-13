package org.qweyns.qweprotectstones.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.event.RegionExplosionTypeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Взрывы: фильтрация блоков по флагу explosion_damage и снятие прочности
 * ядрам через {@link SiegeService}. Конвейер урона (кулдаун, событие,
 * алерты, уничтожение) живёт в сервисе — аддоны взрывчатки работают с ним.
 */
public class RegionExplosionListener implements Listener {

    private final QweProtectStones plugin;
    private final SiegeService siege;

    public RegionExplosionListener(QweProtectStones plugin, SiegeService siege) {
        this.plugin = plugin;
        this.siege = siege;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.getBlock().getLocation(), event.blockList(),
                null, event.getBlock(), "BED", null);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        // поджигавший TNT записывается атакующим, даже если взрыв догнал уже его смерть
        String primer = event.getEntity() instanceof TNTPrimed primed
                && primed.getSource() instanceof Player player ? player.getName() : null;
        handleExplosion(event.getLocation(), event.blockList(),
                event.getEntity(), null, classify(event.getEntityType()), primer);
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

    private void handleExplosion(Location center, List<Block> blockList,
                                 Entity source, Block sourceBlock, String defaultType, String primerName) {
        World world = center.getWorld();
        if (world == null) return;

        Set<Region> affected = new HashSet<>();

        // один проход, индекс даёт O(1), радиусы не нужны

        blockList.removeIf(block -> {
            Region region = plugin.getRegionManager().getRegionAt(block.getLocation());
            if (region == null) return false;

            affected.add(region);
            if (region.isCore(block.getLocation())) return true;
            return !plugin.getProtectionService().flag(region, RegionFlag.EXPLOSION_DAMAGE);
        });

        // у заряда ветра список блоков пуст — регион ищем ещё и по центру взрыва
        Region centerRegion = plugin.getRegionManager().getRegionAt(center);
        if (centerRegion != null) affected.add(centerRegion);

        if (affected.isEmpty()) return;

        // осады выключены, прочность не снимаем, блоки по-прежнему под флагом

        if (!plugin.getConfigManager().isSiegeEnabled()) return;

        // тип взрыва могут подменить аддоны (динамиты, С4) — это ключ к правилам explosions
        RegionExplosionTypeEvent classified = new RegionExplosionTypeEvent(source, sourceBlock, defaultType);
        Bukkit.getPluginManager().callEvent(classified);
        String explosionType = classified.getExplosionType();

        List<Region> damaged = new ArrayList<>();
        for (Region region : affected) {
            RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
            if (type != null && type.explosionDamages(explosionType)) damaged.add(region);
        }

        for (Region region : damaged) {
            Location core = region.getCoreLocation();
            if (core == null) continue;

            // прочность только если рвануло у самого ядра;
            // множитель радиуса приходит от аддонов (динамит A — втрое дальше)

            double radius = explosionRadiusFor(region) * classified.getDamageRadiusMultiplier();
            if (core.distanceSquared(center) <= radius * radius) {
                // штраф за починку — только от взрыва, которому разрешено вредить ядру
                plugin.getPenaltyManager().markAttacked(region);
                siege.damageRegion(region, 1, explosionType, primerName);
            }
        }
    }

    private double explosionRadiusFor(Region region) {
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        return type != null && type.overridesExplosionRadius()
                ? type.explosionDamageRadius()
                : plugin.getConfigManager().getExplosionDamageRadius();
    }
}
