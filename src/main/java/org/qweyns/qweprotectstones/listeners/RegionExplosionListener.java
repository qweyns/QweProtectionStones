package org.qweyns.qweprotectstones.listeners;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.event.RegionDamageEvent;
import org.qweyns.qweprotectstones.regions.event.RegionDeleteEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Механика осад: взрывы не ломают постройки внутри привата, но снимают прочность
 * с ядра. Когда прочность заканчивается — приват падает.
 */
public class RegionExplosionListener implements Listener {

    private final QweProtectStones plugin;

    /** Приваты, для которых уже запланирован снос, — защита от двойного срабатывания. */
    private final Set<UUID> processingRemoval = ConcurrentHashMap.newKeySet();

    /** Кулдаун урона: без него стак ТНТ сносил бы приват за один тик. */
    private final Cache<UUID, Long> lastDamageTime = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    public RegionExplosionListener(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.getBlock().getLocation(), event.blockList(), "BED");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.getLocation(), event.blockList(), classify(event.getEntityType()));
    }

    private String classify(EntityType type) {
        // Остальное (сам ТНТ, вагонетка с ТНТ, гаст) считаем обычным взрывом.
        return switch (type) {
            case WITHER, WITHER_SKULL -> "WITHER";
            case CREEPER -> "CREEPER";
            case END_CRYSTAL -> "ENDER_CRYSTAL";
            default -> "TNT";
        };
    }

    private void handleExplosion(Location center, List<Block> blockList, String explosionType) {
        World world = center.getWorld();
        if (world == null) return;

        Set<Region> affected = new HashSet<>();

        // Один проход по списку блоков: индекс отвечает за O(1), поэтому
        // радиусы больше не нужны — мы точно знаем, что реально задето взрывом.
        blockList.removeIf(block -> {
            Region region = plugin.getRegionManager().getRegionAt(block.getLocation());
            if (region == null) return false;

            affected.add(region);
            if (region.isCore(block.getLocation())) return true; // ядро сносится только по прочности
            return !plugin.getProtectionService().flag(region, RegionFlag.EXPLOSION_DAMAGE);
        });

        if (affected.isEmpty()) return;

        List<Region> damaged = new ArrayList<>();
        for (Region region : affected) {
            plugin.getPenaltyManager().markAttacked(region.getId());

            RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
            if (type != null && type.explosionDamages(explosionType)) damaged.add(region);
        }

        for (Region region : damaged) {
            Location core = region.getCoreLocation();
            if (core == null) continue;

            // Прочность снимается только если рвануло рядом с самим ядром,
            // а не на дальнем краю большой территории.
            double radius = explosionRadiusFor(region);
            if (core.distanceSquared(center) <= radius * radius) {
                damageRegion(region, explosionType);
            }
        }
    }

    /** Радиус берётся у типа привата, а если он его не задаёт — из общей настройки. */
    private double explosionRadiusFor(Region region) {
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        return type != null && type.overridesExplosionRadius()
                ? type.explosionDamageRadius()
                : plugin.getConfigManager().getExplosionDamageRadius();
    }

    private void damageRegion(Region region, String explosionType) {
        if (processingRemoval.contains(region.getId())) return;

        RegionType regionType = plugin.getRegionTypes().byId(region.getTypeId());
        // Тип может быть объявлен неуязвимым для рейдов — тогда прочность не трогаем.
        if (regionType != null && regionType.raidImmune()) return;

        long cooldownTicks = regionType != null && regionType.overridesDamageCooldown()
                ? regionType.damageCooldownTicks()
                : plugin.getConfigManager().getDamageCooldownTicks();
        long cooldownMs = cooldownTicks * 50L;
        long now = System.currentTimeMillis();
        Long last = lastDamageTime.getIfPresent(region.getId());
        if (last != null && now - last < cooldownMs) return;

        RegionDamageEvent event = new RegionDamageEvent(region, explosionType, 1);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getDamage() <= 0) return;

        lastDamageTime.put(region.getId(), now);

        Location core = region.getCoreLocation();
        if (core == null) return;

        String owner = region.getOwnerName().isEmpty()
                ? plugin.getLanguageManager().getRawMessage("unknown_owner")
                : region.getOwnerName();

        region.recordAttack(attackerNameNear(core));
        plugin.getNotificationManager().sendAttackAlert(region, owner, core);
        plugin.getVisualManager().spawnDamageIndicator(core, event.getDamage());
        alertNeighbours(region, core);

        if (region.getDurability() > event.getDamage()) {
            region.setDurability(region.getDurability() - event.getDamage());
            plugin.getRegionStorage().save(region);
            plugin.getVisualManager().playEffect(core, region.getTypeId(), "damage");
            plugin.getHologramManager().createOrUpdateHologram(region);
            return;
        }

        destroyRegion(region, core);
    }

    /** Кто ближе всех к взрыву — тот и записывается нападавшим. */
    private String attackerNameNear(Location core) {
        double radius = plugin.getConfigManager().getExplosionDamageRadius() + 16.0;
        double bestDistance = radius * radius;
        String best = "";

        for (Player nearby : core.getWorld().getPlayers()) {
            double distance = nearby.getLocation().distanceSquared(core);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = nearby.getName();
            }
        }
        return best;
    }

    /** Соседям слышно, что рядом идёт рейд: это часть атмосферы осады. */
    private void alertNeighbours(Region region, Location core) {
        int radius = plugin.getConfigManager().getConfig().getInt("settings.raid_neighbour_radius", 0);
        if (radius <= 0 || core.getWorld() == null) return;

        double radiusSq = (double) radius * radius;
        for (Player nearby : core.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(core) > radiusSq) continue;
            if (region.contains(nearby.getLocation())) continue;

            plugin.getTunables().raidNearby().playTo(nearby);
        }
    }

    /** Прочность кончилась: приват уничтожен рейдом. */
    private void destroyRegion(Region region, Location core) {
        processingRemoval.add(region.getId());

        if (!plugin.getRegionManager().deleteRegion(region, RegionDeleteEvent.Reason.DESTROYED_BY_RAID, null)) {
            processingRemoval.remove(region.getId());
            return;
        }

        plugin.getRegionLifecycleListener().cleanupVisuals(region);
        plugin.getNotificationManager().sendDestroyedAlert(region, core);

        plugin.getSchedulers().runAtLocationLater(core, () -> {
            try {
                if (core.getWorld() != null && core.getBlock().getType() == materialOf(region)) {
                    core.getBlock().setType(Material.AIR);
                }
            } finally {
                processingRemoval.remove(region.getId());
            }
        }, 1L);
    }

    private Material materialOf(Region region) {
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        return type != null ? type.material() : Material.AIR;
    }
}
