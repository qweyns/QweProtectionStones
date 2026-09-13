package org.qweyns.qweprotectstones.features.maintenance;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.event.RegionDeleteEvent;
import org.qweyns.qweprotectstones.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AbandonedRegionTask {

    private final QweProtectStones plugin;

    public AbandonedRegionTask(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    private Schedulers.Task task;

    public void start() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!isEnabled()) return;

        long intervalTicks = TimeUnit.MINUTES.toSeconds(checkIntervalMinutes()) * 20L;

        task = plugin.getSchedulers().runTimer(this::sweep, 20L * 60, intervalTicks);

        plugin.getLogger().info("Автоочистка заброшенных приватов включена: срок "
                + inactiveDays() + " дн., проверка каждые " + checkIntervalMinutes() + " мин.");
    }

    private boolean isEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("settings.abandoned.enable", false);
    }

    private int inactiveDays() {
        return Math.max(1, plugin.getConfigManager().getConfig().getInt("settings.abandoned.inactive_days", 60));
    }

    private int checkIntervalMinutes() {
        return Math.max(5, plugin.getConfigManager().getConfig().getInt("settings.abandoned.check_interval_minutes", 60));
    }

    private boolean keepUpgraded() {
        return plugin.getConfigManager().getConfig().getBoolean("settings.abandoned.keep_upgraded", true);
    }

    public void sweep() {
        plugin.getSchedulers().runAsync(() -> {
            Map<UUID, Long> lastSeen = plugin.getRegionStorage().loadLastSeen();
            plugin.getSchedulers().runNextTick(() -> removeAbandoned(lastSeen));
        });
    }

    private void removeAbandoned(Map<UUID, Long> lastSeen) {
        long threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(inactiveDays());
        List<Region> doomed = new ArrayList<>();

        for (Region region : plugin.getRegionManager().getAllRegions()) {
            UUID owner = region.getOwnerId();
            if (owner == null) continue;
            if (Bukkit.getPlayer(owner) != null) continue;

            // нет записи о входе, отсчёт от создания
            long seen = lastSeen.getOrDefault(owner, region.getCreatedAt());
            if (seen > threshold) continue;

            if (keepUpgraded() && region.getDurability() > startDurabilityOf(region)) continue;

            // выставленные на продажу и в аренду не трогаем — вместе с приватом сгорали бы сделки
            if (skipListed() && isListed(region)) continue;

            doomed.add(region);
        }

        if (doomed.isEmpty()) return;

        int removed = 0;
        for (Region region : doomed) {
            if (!plugin.getRegionManager().deleteRegion(region, RegionDeleteEvent.Reason.EXPIRED, null)) continue;

            plugin.getRegionLifecycleListener().cleanupVisuals(region);
            clearCoreBlock(region);
            removed++;
        }
        plugin.getLogger().info("Автоочистка: удалено заброшенных приватов — " + removed);
    }

    private boolean skipListed() {
        return plugin.getConfigManager().getConfig().getBoolean("settings.abandoned.skip_listed", true);
    }

    private boolean isListed(Region region) {
        return plugin.getMarketManager().getSale(region) != null
                || plugin.getMarketManager().getRental(region) != null;
    }

    private int startDurabilityOf(Region region) {
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        return type != null ? type.startDurability() : 1;
    }

    private void clearCoreBlock(Region region) {
        Location core = region.getCoreLocation();
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        if (core == null || type == null) return;

        plugin.getSchedulers().runAtLocation(core, () -> {
            if (core.getBlock().getType() == type.material()) core.getBlock().setType(Material.AIR);
        });
    }
}
