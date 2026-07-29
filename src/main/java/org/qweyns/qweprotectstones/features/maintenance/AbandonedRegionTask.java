package org.qweyns.qweprotectstones.features.maintenance;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.event.RegionDeleteEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Автоочистка заброшенных приватов: если владелец не заходил дольше заданного
 * срока, территория освобождается.
 *
 * <p>Прокачанные приваты можно защитить настройкой {@code keep_upgraded}: жалко
 * сносить базу, в которую вложили ресурсы, только потому что игрок ушёл в отпуск.</p>
 */
public class AbandonedRegionTask {

    private final QweProtectStones plugin;

    public AbandonedRegionTask(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!isEnabled()) return;

        long intervalTicks = TimeUnit.MINUTES.toSeconds(checkIntervalMinutes()) * 20L;
        // Первый прогон не сразу после запуска: сервер ещё догружает миры.
        plugin.getSchedulers().runTimer(this::sweep, 20L * 60, intervalTicks);

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

    /** Один проход очистки. Тяжёлое чтение базы уходит в асинхронный поток. */
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

            // Нет записи о входе — считаем моментом отсчёта создание привата.
            long seen = lastSeen.getOrDefault(owner, region.getCreatedAt());
            if (seen > threshold) continue;

            if (keepUpgraded() && region.getDurability() > startDurabilityOf(region)) continue;

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

    private int startDurabilityOf(Region region) {
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        return type != null ? type.startDurability() : 1;
    }

    /** Блок-ядро убираем в потоке его региона: на Folia иначе нельзя. */
    private void clearCoreBlock(Region region) {
        Location core = region.getCoreLocation();
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        if (core == null || type == null) return;

        plugin.getSchedulers().runAtLocation(core, () -> {
            if (core.getBlock().getType() == type.material()) core.getBlock().setType(Material.AIR);
        });
    }
}
