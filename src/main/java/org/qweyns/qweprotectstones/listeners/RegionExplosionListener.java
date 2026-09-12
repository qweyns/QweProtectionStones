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
import org.bukkit.entity.TNTPrimed;
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

public class RegionExplosionListener implements Listener {

    private final QweProtectStones plugin;

    private final Set<UUID> processingRemoval = ConcurrentHashMap.newKeySet();

    // TTL не короче удвоенного кулдауна урона, иначе длинный кулдаун молча отключался
    private Cache<UUID, Long> lastDamageTime;

    public RegionExplosionListener(QweProtectStones plugin) {
        this.plugin = plugin;
        rebuildDamageCache();
    }

    private void rebuildDamageCache() {
        long maxCooldownTicks = plugin.getConfigManager().getDamageCooldownTicks();
        for (RegionType type : plugin.getRegionTypes().all()) {
            if (type.overridesDamageCooldown()) maxCooldownTicks = Math.max(maxCooldownTicks, type.damageCooldownTicks());
        }
        long ttl = Math.max(TimeUnit.MINUTES.toMillis(5), maxCooldownTicks * 50L * 2);
        lastDamageTime = CacheBuilder.newBuilder()
                .expireAfterWrite(ttl, TimeUnit.MILLISECONDS)
                .build();
    }

    /** Кулдаун урона мог измениться в конфиге — при /reload строим кеш заново. */
    public void reload() {
        Cache<UUID, Long> previous = lastDamageTime;
        rebuildDamageCache();
        previous.invalidateAll();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.getBlock().getLocation(), event.blockList(), "BED", null);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        // поджигавший TNT записывается атакующим, даже если взрыв догнал уже его смерть
        String primer = event.getEntity() instanceof TNTPrimed primed
                && primed.getSource() instanceof Player player ? player.getName() : null;
        handleExplosion(event.getLocation(), event.blockList(), classify(event.getEntityType()), primer);
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

    private void handleExplosion(Location center, List<Block> blockList, String explosionType, String primerName) {
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

        List<Region> damaged = new ArrayList<>();
        for (Region region : affected) {
            RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
            if (type != null && type.explosionDamages(explosionType)) damaged.add(region);
        }

        for (Region region : damaged) {
            Location core = region.getCoreLocation();
            if (core == null) continue;

            // прочность только если рвануло у самого ядра

            double radius = explosionRadiusFor(region);
            if (core.distanceSquared(center) <= radius * radius) {
                // штраф за починку — только от взрыва, которому разрешено вредить ядру
                plugin.getPenaltyManager().markAttacked(region);
                damageRegion(region, explosionType, primerName);
            }
        }
    }

    private double explosionRadiusFor(Region region) {
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        return type != null && type.overridesExplosionRadius()
                ? type.explosionDamageRadius()
                : plugin.getConfigManager().getExplosionDamageRadius();
    }

    private void damageRegion(Region region, String explosionType, String primerName) {
        if (processingRemoval.contains(region.getId())) return;

        RegionType regionType = plugin.getRegionTypes().byId(region.getTypeId());

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
                ? plugin.getLanguageManager().rawTemplate("unknown_owner")
                : region.getOwnerName();

        region.recordAttack(primerName != null ? primerName : attackerNameNear(region, core));

        if (region.getDurability() > event.getDamage()) {
            region.setDurability(region.getDurability() - event.getDamage());
            plugin.getRegionStorage().save(region);

            // алерт после списания, %durability% уже актуальный

            plugin.getNotificationManager().sendAttackAlert(region, owner, core);
            plugin.getVisualManager().spawnDamageIndicator(core, event.getDamage());
            plugin.getVisualManager().playEffect(core, region.getTypeId(), "damage");
            plugin.getHologramManager().createOrUpdateHologram(region);
            alertNeighbours(region, core);
            return;
        }

        plugin.getVisualManager().spawnDamageIndicator(core, event.getDamage());
        alertNeighbours(region, core);

        destroyRegion(region, core);
    }

    private String attackerNameNear(Region region, Location core) {
        // мир мог выгрузиться между поджигом и взрывом

        if (core == null || core.getWorld() == null) return "";

        double radius = plugin.getConfigManager().getExplosionDamageRadius() + 16.0;
        double bestDistance = radius * radius;
        String best = "";

        for (Player nearby : core.getWorld().getPlayers()) {
            // владелец и участники свой приват не штурмуют: TNT, заложенная заранее,
            // не должна записывать атакующим того, кто просто стоит рядом
            if (region.isOwner(nearby.getUniqueId()) || region.getTrust(nearby.getUniqueId()) != null) continue;
            double distance = nearby.getLocation().distanceSquared(core);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = nearby.getName();
            }
        }
        return best;
    }

    private void alertNeighbours(Region region, Location core) {
        int radius = plugin.getConfigManager().getConfig().getInt("siege.neighbour_alert_radius", 0);
        if (radius <= 0 || core.getWorld() == null) return;

        double radiusSq = (double) radius * radius;
        for (Player nearby : core.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(core) > radiusSq) continue;
            if (region.contains(nearby.getLocation())) continue;

            plugin.getTunables().raidNearby().playTo(nearby);
        }
    }

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
                // за тик в этот блок мог встать новый приват: его ядро нельзя сносить
                Region replacement = plugin.getRegionManager().getRegionAt(core);
                boolean occupiedByNewCore = replacement != null && replacement.isCore(core);
                if (core.getWorld() != null && !occupiedByNewCore
                        && core.getBlock().getType() == materialOf(region)) {
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
