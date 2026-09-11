package org.qweyns.qweprotectstones.features.teleport;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.scheduler.Schedulers;
import org.qweyns.qweprotectstones.regions.Region;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Задержка телепорта {@code /ps home}: телепорт не мгновенный, чтобы им нельзя
 * было убегать из боя. Срывается движением или уроном; между использованиями —
 * перезарядка.
 *
 * <p>Все параметры — секция {@code home} в config.yml:</p>
 * <ul>
 *   <li>{@code home.warmup-seconds} — задержка (0 = мгновенно, как раньше);</li>
 *   <li>{@code home.cancel-on-move} — срывать при движении;</li>
 *   <li>{@code home.cancel-on-damage} — срывать при получении урона;</li>
 *   <li>{@code home.cooldown-seconds} — перезарядка между телепортами.</li>
 * </ul>
 */
public class HomeWarmup implements Listener {

    private final QweProtectStones plugin;

    /** Ожидаемый телепорт: задача и цель, чтобы можно было снять. */
    private record Pending(Schedulers.Task task, Location home, String regionShortId) {
    }

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public HomeWarmup(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    /** Мгновенный ли телепорт (0 секунд задержки). */
    public boolean isInstant() {
        return warmupSeconds() <= 0;
    }

    /**
     * Запускает телепорт с учётом задержки и перезарядки. Сам сообщает игроку
     * обо всех отказах.
     *
     * @return true, если телепорт запущен (или выполнен мгновенно)
     */
    public boolean teleport(Player player, Region region, Location home) {
        int cooldown = cooldownSeconds();
        if (cooldown > 0) {
            Long last = cooldowns.get(player.getUniqueId());
            if (last != null) {
                long elapsed = (System.currentTimeMillis() - last) / 1000L;
                if (elapsed < cooldown) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("home_cooldown",
                            "%seconds%", String.valueOf(cooldown - elapsed)));
                    return false;
                }
            }
        }

        int warmup = warmupSeconds();
        if (warmup <= 0) {
            doTeleport(player, home, region.getShortId());
            return true;
        }

        // Повторная команда до истечения задержки не должна плодить задачи:
        // снимаем прежнее ожидание (без сообщения) и начинаем отсчёт заново.
        Pending previous = pending.remove(player.getUniqueId());
        if (previous != null) previous.task().cancel();

        Schedulers.Task task = plugin.getSchedulers().runLater(
                () -> finish(player.getUniqueId()), warmup * 20L);
        pending.put(player.getUniqueId(), new Pending(task, home, region.getShortId()));

        player.sendMessage(plugin.getLanguageManager().getMessage("home_warmup",
                "%seconds%", String.valueOf(warmup), "%id%", region.getShortId()));
        return true;
    }

    private void finish(UUID playerId) {
        Pending entry = pending.remove(playerId);
        if (entry == null) return;

        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        doTeleport(player, entry.home(), entry.regionShortId());
    }

    private void doTeleport(Player player, Location home, String regionShortId) {
        // Сохраняем направление взгляда, чтобы телепорт не крутил камеру.
        home.setYaw(player.getLocation().getYaw());
        home.setPitch(player.getLocation().getPitch());

        player.teleportAsync(home).thenAccept(success -> {
            if (Boolean.TRUE.equals(success)) {
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                player.sendMessage(plugin.getLanguageManager().getMessage("home_teleported",
                        "%id%", regionShortId));
            }
        });
    }

    /** Снимает ожидание и сообщает причину. */
    private void cancel(UUID playerId, String messageKey) {
        Pending entry = pending.remove(playerId);
        if (entry == null) return;

        entry.task().cancel();
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(plugin.getLanguageManager().getMessage(messageKey, "%id%", entry.regionShortId()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getConfigManager().getConfig().getBoolean("home.cancel-on-move", true)) return;
        if (pending.isEmpty()) return;

        // Поворот головы — не движение: реагируем только на смену блока.
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        cancel(event.getPlayer().getUniqueId(), "home_cancelled_move");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!plugin.getConfigManager().getConfig().getBoolean("home.cancel-on-damage", true)) return;
        if (pending.isEmpty()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        cancel(player.getUniqueId(), "home_cancelled_damage");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    private int warmupSeconds() {
        return Math.max(0, plugin.getConfigManager().getConfig().getInt("home.warmup-seconds", 0));
    }

    private int cooldownSeconds() {
        return Math.max(0, plugin.getConfigManager().getConfig().getInt("home.cooldown-seconds", 0));
    }
}
