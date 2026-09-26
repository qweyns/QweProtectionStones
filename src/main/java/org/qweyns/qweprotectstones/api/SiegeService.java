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
 * на Folia это поток ядра. Для чужого потока используйте damageRegionAsync.</p>
 */
public class SiegeService {

    private final QweProtectStones plugin;
    private volatile boolean closed;
    private final Set<java.util.concurrent.CompletableFuture<Boolean>> pending = ConcurrentHashMap.newKeySet();

    public void close() {
        closed = true;
        pending.forEach(future -> future.complete(false));
        pending.clear();
    }

    // TTL не короче удвоенного кулдауна урона, иначе длинный кулдаун молча отключался
    private volatile Cache<UUID, Long> lastDamageTime;

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
        if (closed || region == null || damage <= 0) return false;
        if (!plugin.getConfigManager().isSiegeEnabled() || !isDamaging(region, explosionType)) return false;
        if (plugin.getRegionManager().getById(region.getId()) != region) return false;
        Location core = region.getCoreLocation();
        if (core == null) return false;
        if (!plugin.getSchedulers().ownsLocation(core))
            throw new IllegalStateException("Урон осаде должен выполняться в потоке ядра; используйте damageRegionAsync");
        try (Region.Operation operation = region.tryOperation()) {
            if (operation == null || cooldownRemainingMs(region) > 0) return false;
            RegionDamageEvent event = new RegionDamageEvent(region, explosionType, damage, attackerName);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || event.getDamage() <= 0
                    || plugin.getRegionManager().getById(region.getId()) != region
                    || !plugin.getConfigManager().isSiegeEnabled() || !isDamaging(region, explosionType)) return false;

            boolean lethal = region.getDurability() <= event.getDamage();
            // Veto удаления означает отсутствие урона, штрафа, cooldown и алерта.
            if (lethal && !plugin.getRegionManager().deleteWithin(region,
                    RegionDeleteEvent.Reason.DESTROYED_BY_RAID, null, operation)) return false;
            synchronized (region) {
                region.setDurability(Math.max(0, region.getDurability() - event.getDamage()));
                region.recordAttack(attackerNameNear(region, core, attackerName));
            }
            lastDamageTime.put(region.getId(), System.currentTimeMillis());
            if (!lethal) {
                // Штраф только за принятый урон; одинаково для API и Bukkit-взрыва.
                plugin.getPenaltyManager().markAttacked(region);
                plugin.getRegionStorage().save(region);
                plugin.getNotificationManager().sendAttackAlert(region, region.getOwnerName(), core);
                plugin.getVisualManager().playEffect(core, region.getTypeId(), "damage");
                plugin.getHologramManager().createOrUpdateHologram(region);
            } else {
                plugin.getRegionLifecycleListener().cleanupVisuals(region);
                plugin.getNotificationManager().sendDestroyedAlert(region, core);
                org.qweyns.qweprotectstones.utils.CoreBlocks.clearLater(plugin, region, core);
            }
            plugin.getVisualManager().spawnDamageIndicator(core, event.getDamage());
            alertNeighbours(region, core);
            return true;
        }
    }

    /** Межрегиональный вызов: результат приходит после работы в потоке ядра. Не вызывать join() на тике. */
    public java.util.concurrent.CompletableFuture<Boolean> damageRegionAsync(Region region, int damage,
                                                                          String type, String attacker) {
        var result = new java.util.concurrent.CompletableFuture<Boolean>();
        Location core = region == null ? null : region.getCoreLocation();
        if (core == null || !plugin.isEnabled()) { result.complete(false); return result; }
        pending.add(result);
        result.whenComplete((value, error) -> pending.remove(result));
        if (closed) { result.complete(false); return result; }
        try {
            plugin.getSchedulers().runAtLocation(core, () -> {
                try { result.complete(damageRegion(region, damage, type, attacker)); }
                catch (Throwable e) { result.completeExceptionally(e); }
            });
        } catch (RuntimeException e) { result.completeExceptionally(e); }
        return result;
    }

    private String attackerNameNear(Region region, Location core, String primerName) {
        if (primerName != null) return primerName;
        // Угадывание по ближайшему игроку часто обвиняет прохожего — только по желанию.
        if (!plugin.getConfigManager().getConfig().getBoolean("siege.guess_attacker", false)) return "";
        // Не приписываем атаку случайному игроку из чужого Folia-региона.
        if (plugin.getSchedulers().isFolia()) return "";

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

    private final java.util.Map<UUID, Long> lastNeighbourAlert = new ConcurrentHashMap<>();

    private void alertNeighbours(Region region, Location core) {
        int radius = plugin.getConfigManager().getConfig().getInt("siege.neighbour_alert_radius", 0);
        if (radius <= 0 || core.getWorld() == null) return;

        // TNT-пушка даёт десятки взрывов в секунду: соседей оповещаем не чаще раза в N секунд на приват
        long cooldownMs = Math.max(0, plugin.getConfigManager().getConfig()
                .getLong("siege.neighbour_alert_cooldown_seconds", 10)) * 1000L;
        long now = System.currentTimeMillis();
        Long previous = lastNeighbourAlert.get(region.getId());
        if (previous != null && now - previous < cooldownMs) return;
        lastNeighbourAlert.put(region.getId(), now);
        if (lastNeighbourAlert.size() > 4096) lastNeighbourAlert.values().removeIf(at -> now - at > cooldownMs);

        // задачи — только игрокам того же мира, а не всему онлайну
        for (Player nearby : core.getWorld().getPlayers()) {
            plugin.getSchedulers().runAtEntity(nearby, () -> {
                Location at = nearby.getLocation();
                if (at.getWorld() == core.getWorld() && at.distanceSquared(core) <= (double) radius * radius)
                    plugin.getTunables().raidNearby().playTo(nearby);
            });
        }
    }
}
