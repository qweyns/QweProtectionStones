package org.qweyns.qweprotectstones.api;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.event.RegionDamageEvent;
import org.qweyns.qweprotectstones.regions.event.RegionDeleteEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Сервис осады: единый конвейер снятия прочности привата взрывом.
 *
 * <p>Используется самим плагином (листенер взрывов) и доступен аддонам
 * через {@link QpsApi#getSiegeService()}. Все методы звать в потоке сервера —
 * в том, в котором случился взрыв.</p>
 */
public class SiegeService {

    private final QweProtectStones plugin;

    // повторные взрывы в приват, который уже удаляется, не проходят
    private final Set<UUID> processingRemoval = ConcurrentHashMap.newKeySet();

    // TTL не короче удвоенного кулдауна урона, иначе длинный кулдаун молча отключался
    private Cache<UUID, Long> lastDamageTime;

    public SiegeService(QweProtectStones plugin) {
        this.plugin = plugin;
        rebuildDamageCache();
    }

    /** Кулдаун урона мог измениться в конфиге — при /reload строим кеш заново. */
    public void reload() {
        Cache<UUID, Long> previous = lastDamageTime;
        rebuildDamageCache();
        previous.invalidateAll();
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

    /**
     * Вредит ли взрыв данного типа прочности привата: тип привата не
     * {@code raid_immune} и разрешает тип взрыва в секции {@code explosions}.
     * Кулдаун здесь не учитывается — см. {@link #cooldownRemainingMs(Region)}.
     */
    public boolean isDamaging(Region region, String explosionType) {
        if (region == null || explosionType == null) return false;
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        return type != null && !type.raidImmune() && type.explosionDamages(explosionType);
    }

    /** Сколько ещё действует кулдаун урона привата (0 — урон пройдёт). */
    public long cooldownRemainingMs(Region region) {
        if (region == null) return 0;
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        long cooldownTicks = type != null && type.overridesDamageCooldown()
                ? type.damageCooldownTicks()
                : plugin.getConfigManager().getDamageCooldownTicks();
        if (cooldownTicks <= 0) return 0;

        Long last = lastDamageTime.getIfPresent(region.getId());
        if (last == null) return 0;
        return Math.max(0, last + cooldownTicks * 50L - System.currentTimeMillis());
    }

    /**
     * Снять прочность привата — полный конвейер плагина: кулдаун,
     * {@link RegionDamageEvent} (аддоны могут изменить урон или отменить),
     * алерты владельцу, индикатор урона, голограмма, а на нуле — уничтожение
     * привата с причиной {@link RegionDeleteEvent.Reason#DESTROYED_BY_RAID}.
     *
     * <p>Именно этот метод зовёт сам плагин при обычном взрыве; аддону он нужен
     * для нестандартных случаев — например, направленный урон без физического
     * взрыва или «Разрывная волна», сносящая сразу 2 единицы прочности.</p>
     *
     * @param damage        сколько единиц прочности снять (1 — обычный взрыв)
     * @param explosionType тип взрыва для правил {@code explosions} у типа привата
     * @param attackerName  кто атакует (для статистики атак и алертов), может быть null
     * @return true — урон прошёл; false — кулдаун, иммунитет или отмена события
     */
    public boolean damageRegion(Region region, int damage, String explosionType, String attackerName) {
        if (region == null || damage <= 0) return false;
        if (processingRemoval.contains(region.getId())) return false;

        RegionType regionType = plugin.getRegionTypes().byId(region.getTypeId());
        if (regionType != null && regionType.raidImmune()) return false;

        long cooldownTicks = regionType != null && regionType.overridesDamageCooldown()
                ? regionType.damageCooldownTicks()
                : plugin.getConfigManager().getDamageCooldownTicks();
        long cooldownMs = cooldownTicks * 50L;
        long now = System.currentTimeMillis();
        Long last = lastDamageTime.getIfPresent(region.getId());
        if (last != null && now - last < cooldownMs) return false;

        RegionDamageEvent event = new RegionDamageEvent(region, explosionType, damage, attackerName);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getDamage() <= 0) return false;

        lastDamageTime.put(region.getId(), now);

        Location core = region.getCoreLocation();
        if (core == null) return false;

        String owner = region.getOwnerName().isEmpty()
                ? plugin.getLanguageManager().rawTemplate("unknown_owner")
                : region.getOwnerName();

        region.recordAttack(attackerNameNear(region, core, attackerName));

        if (region.getDurability() > event.getDamage()) {
            region.setDurability(region.getDurability() - event.getDamage());
            plugin.getRegionStorage().save(region);

            // алерт после списания, %durability% уже актуальный

            plugin.getNotificationManager().sendAttackAlert(region, owner, core);
            plugin.getVisualManager().spawnDamageIndicator(core, event.getDamage());
            plugin.getVisualManager().playEffect(core, region.getTypeId(), "damage");
            plugin.getHologramManager().createOrUpdateHologram(region);
            alertNeighbours(region, core);
            return true;
        }

        plugin.getVisualManager().spawnDamageIndicator(core, event.getDamage());
        alertNeighbours(region, core);

        destroyRegion(region, core);
        return true;
    }

    private String attackerNameNear(Region region, Location core, String primerName) {
        if (primerName != null) return primerName;

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
        if (radius <= 0) return;

        for (Player nearby : core.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(core) > (double) radius * radius) continue;

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
