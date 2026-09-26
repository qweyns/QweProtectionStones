package org.qweyns.qweprotectstones.features.log;

import org.qweyns.qweprotectstones.config.ConfigValues;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.storage.dao.RegionLogEntry;
import org.qweyns.qweprotectstones.scheduler.Schedulers;

import java.util.concurrent.TimeUnit;

public class RegionActionLogger implements Listener {

    private final QweProtectStones plugin;

    public RegionActionLogger(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    private boolean isEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("settings.action_log.enable", true);
    }

    private boolean logContainers() {
        return plugin.getConfigManager().getConfig().getBoolean("settings.action_log.log_containers", true);
    }

    private Schedulers.Task pruneTask;

    public void startPruning() {
        if (pruneTask != null) {
            pruneTask.cancel();
            pruneTask = null;
        }
        if (!isEnabled()) return;

        long period = ConfigValues.boundedLong(plugin.getConfigManager().getConfig(), "settings.action_log.prune_interval_minutes", 1440L, 1L, 525600L) * 1200L;
        long delay = ConfigValues.boundedLong(plugin.getConfigManager().getConfig(), "settings.action_log.prune_initial_delay_seconds", 120L, 1L, 86400L) * 20L;
        pruneTask = plugin.getSchedulers().runAsyncTimer(() -> {
            // срок читаем каждый раз — переживает /reload без рестарта задачи
            int keepDays = Math.max(1, plugin.getConfigManager().getConfig().getInt("settings.action_log.keep_days", 14));
            int removed = plugin.getRegionStorage().pruneLog(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(keepDays));
            if (removed > 0) plugin.getLogger().info("Журнал действий: удалено старых записей — " + removed);
        }, delay, period);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        record(event.getPlayer(), event.getBlock().getLocation(), "break", event.getBlock().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        record(event.getPlayer(), event.getBlockPlaced().getLocation(), "place", event.getBlockPlaced().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onContainerOpen(InventoryOpenEvent event) {
        if (!logContainers()) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        Location location = event.getInventory().getLocation();
        if (location == null) return;

        record(player, location, "open", event.getInventory().getType().name());
    }

    private void record(Player player, Location location, String action, String detail) {
        if (!isEnabled()) return;

        Region region = plugin.getRegionManager().getRegionAt(location);
        // владельца не пишем, журнал про доверенных
        if (region == null || region.isOwner(player.getUniqueId())) return;

        plugin.getRegionStorage().log(RegionLogEntry.of(region.getId(), player.getName(), action,
                detail + " @ " + location.getBlockX() + "/" + location.getBlockY() + "/" + location.getBlockZ()));
    }
}
