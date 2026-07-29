package org.qweyns.qweprotectstones.features.log;

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

import java.util.concurrent.TimeUnit;

/**
 * Журнал действий в привате. Отвечает на главный вопрос владельца: «кто из
 * своих это сломал».
 *
 * <p>Пишутся только действия доверенных игроков — постороннего защита и так не
 * пропустит, а действия самого владельца засоряли бы журнал. Записи копятся в
 * очереди и уходят в базу пачкой вместе с обычным сохранением.</p>
 */
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

    /** Запускает периодическую очистку старых записей. */
    public void startPruning() {
        if (!isEnabled()) return;

        int keepDays = Math.max(1, plugin.getConfigManager().getConfig().getInt("settings.action_log.keep_days", 14));
        long dayTicks = 20L * 60 * 60 * 24;

        plugin.getSchedulers().runAsyncTimer(() -> {
            int removed = plugin.getRegionStorage().pruneLog(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(keepDays));
            if (removed > 0) plugin.getLogger().info("Журнал действий: удалено старых записей — " + removed);
        }, 20L * 120, dayTicks);
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
        if (location == null) return; // рюкзаки и виртуальные меню игнорируем

        record(player, location, "open", event.getInventory().getType().name());
    }

    private void record(Player player, Location location, String action, String detail) {
        if (!isEnabled()) return;

        Region region = plugin.getRegionManager().getRegionAt(location);
        // Владельца не пишем: журнал нужен, чтобы разбираться с доверенными.
        if (region == null || region.isOwner(player.getUniqueId())) return;

        plugin.getRegionStorage().log(RegionLogEntry.of(region.getId(), player.getName(), action,
                detail + " @ " + location.getBlockX() + "/" + location.getBlockY() + "/" + location.getBlockZ()));
    }
}
