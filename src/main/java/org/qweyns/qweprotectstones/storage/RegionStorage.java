package org.qweyns.qweprotectstones.storage;

import org.qweyns.qweprotectstones.scheduler.Schedulers;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionManager;
import org.qweyns.qweprotectstones.storage.dao.RegionDao;
import org.qweyns.qweprotectstones.storage.dao.RegionLogEntry;
import org.qweyns.qweprotectstones.storage.dao.MysqlRegionDao;
import org.qweyns.qweprotectstones.storage.dao.SqliteRegionDao;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class RegionStorage {

    private final QweProtectStones plugin;
    private RegionDao dao;
    private Schedulers.Task flushTask;

    private final Map<UUID, Region> pendingSaves = new ConcurrentHashMap<>();
    private final Set<UUID> pendingDeletes = ConcurrentHashMap.newKeySet();
    private final java.util.Queue<RegionLogEntry> pendingLog = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final Map<UUID, org.qweyns.qweprotectstones.features.market.RegionSale> pendingSales = new ConcurrentHashMap<>();
    private final Set<UUID> pendingSaleDeletes = ConcurrentHashMap.newKeySet();
    private final Map<UUID, org.qweyns.qweprotectstones.features.market.RegionRental> pendingRentals = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRentalDeletes = ConcurrentHashMap.newKeySet();
    private volatile long lastFlushMillis;

    public RegionStorage(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public List<Region> init() {
        String dbType = plugin.getConfigManager().getConfig()
                .getString("database.type", "SQLITE").toUpperCase(Locale.ROOT);

        if (dbType.equals("MYSQL")) {
            dao = new MysqlRegionDao(plugin);
        } else {
            if (!dbType.equals("SQLITE")) {
                plugin.getLogger().warning("Неизвестный database.type: '" + dbType
                        + "'. Поддерживаются SQLITE и MYSQL — использую SQLITE.");
                dbType = "SQLITE";
            }
            dao = new SqliteRegionDao(plugin);
        }

        dao.init();
        List<Region> loaded = dao.loadAll();

        plugin.getLogger().info("Загружено приватов: " + loaded.size() + " (база " + dbType + ").");
        restartFlushTask();
        return loaded;
    }

    /** Период флаша из конфига — вызывается и при /reload. */
    public void restartFlushTask() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        long flushPeriod = plugin.getTunables().dbFlushTicks();
        flushTask = plugin.getSchedulers().runAsyncTimer(this::flush, flushPeriod, flushPeriod);
    }

    public void save(Region region) {
        if (region == null || !isLive(region)) return;
        // удаление не снимается: flush пишет удаления после сохранений, при гонке побеждает удаление
        pendingSaves.put(region.getId(), region);
    }

    /** Жив ли регион: удалённые из менеджера не должны возвращаться в базу. */
    private boolean isLive(Region region) {
        RegionManager manager = plugin.getRegionManager();
        return manager != null && manager.getById(region.getId()) == region;
    }

    public long lastFlushMillis() {
        return lastFlushMillis;
    }

    public int pendingCount() {
        return pendingSaves.size() + pendingDeletes.size() + pendingLog.size()
                + pendingSales.size() + pendingSaleDeletes.size()
                + pendingRentals.size() + pendingRentalDeletes.size();
    }

    public void saveNow(Region region) {
        if (region == null || !isLive(region)) return;
        pendingSaves.remove(region.getId());
        plugin.getSchedulers().runAsync(() -> dao.saveAll(List.of(region)));
    }

    /**
     * Снимок для /qps save: через общую очередь, а не напрямую в DAO — иначе запись
     * обгоняла отложенные удаления и возвращала в базу снесённые приваты.
     * Возвращает число реально поставленных в очередь регионов.
     */
    public int saveSnapshot(java.util.Collection<Region> regions) {
        if (dao == null || regions == null) return 0;
        int count = 0;
        for (Region region : regions) {
            if (region == null || !isLive(region)) continue;
            pendingSaves.put(region.getId(), region);
            count++;
        }
        flush();
        return count;
    }

    public void delete(UUID regionId) {
        if (regionId == null) return;
        pendingSaves.remove(regionId);
        pendingDeletes.add(regionId);
    }

    public void loadAutoAddAsync(UUID uuid, BiConsumer<Set<String>, Boolean> callback) {
        // колбэк прыгает на главный поток: в нём трогают игроков
        plugin.getSchedulers().runAsync(() -> dao.loadAutoAdd(uuid,
                (friends, toggledOff) -> plugin.getSchedulers().runNextTick(() -> callback.accept(friends, toggledOff))));
    }

    public void saveAutoAddAsync(UUID uuid, Set<String> friends, boolean toggledOff) {
        Set<String> snapshot = Set.copyOf(friends);
        plugin.getSchedulers().runAsync(() -> dao.saveAutoAdd(uuid, snapshot, toggledOff));
    }

    public void saveAutoAddSync(UUID uuid, Set<String> friends, boolean toggledOff) {
        if (dao != null) dao.saveAutoAdd(uuid, friends, toggledOff);
    }

    public void touchPlayerAsync(UUID uuid, String name) {
        long now = System.currentTimeMillis();
        plugin.getSchedulers().runAsync(() -> dao.touchPlayer(uuid, name, now));
    }

    public Map<UUID, Long> loadLastSeen() {
        return dao == null ? Map.of() : dao.loadLastSeen();
    }

    public void log(RegionLogEntry entry) {
        if (entry != null) pendingLog.add(entry);
    }

    public void readLogAsync(UUID regionId, int limit, java.util.function.Consumer<List<RegionLogEntry>> callback) {
        plugin.getSchedulers().runAsync(() -> {
            List<RegionLogEntry> entries = dao.readLog(regionId, limit);
            plugin.getSchedulers().runNextTick(() -> callback.accept(entries));
        });
    }

    public int pruneLog(long olderThan) {
        return dao == null ? 0 : dao.pruneLog(olderThan);
    }

    private void flush() {
        if (dao == null) return;
        lastFlushMillis = System.currentTimeMillis();

        if (!pendingSaves.isEmpty()) {
            // забираем по ключу, правка во время флаша не теряется
            List<Region> toSave = new ArrayList<>(pendingSaves.size());
            for (UUID id : List.copyOf(pendingSaves.keySet())) {
                Region region = pendingSaves.remove(id);
                if (region != null) toSave.add(region);
            }
            dao.saveAll(toSave);
        }

        if (!pendingDeletes.isEmpty()) {
            List<UUID> toDelete = List.copyOf(pendingDeletes);
            pendingDeletes.removeAll(toDelete);
            dao.deleteAll(toDelete);
        }

        if (!pendingLog.isEmpty()) {
            List<RegionLogEntry> entries = new ArrayList<>();
            for (RegionLogEntry entry = pendingLog.poll(); entry != null; entry = pendingLog.poll()) {
                entries.add(entry);
            }
            dao.appendLog(entries);
        }

        for (UUID id : List.copyOf(pendingSales.keySet())) {
            org.qweyns.qweprotectstones.features.market.RegionSale sale = pendingSales.remove(id);
            if (sale != null) dao.saveSale(sale);
        }
        if (!pendingSaleDeletes.isEmpty()) {
            List<UUID> toDelete = List.copyOf(pendingSaleDeletes);
            pendingSaleDeletes.removeAll(toDelete);
            for (UUID id : toDelete) dao.deleteSale(id);
        }

        for (UUID id : List.copyOf(pendingRentals.keySet())) {
            org.qweyns.qweprotectstones.features.market.RegionRental rental = pendingRentals.remove(id);
            if (rental != null) dao.saveRental(rental);
        }
        if (!pendingRentalDeletes.isEmpty()) {
            List<UUID> toDelete = List.copyOf(pendingRentalDeletes);
            pendingRentalDeletes.removeAll(toDelete);
            for (UUID id : toDelete) dao.deleteRental(id);
        }
    }

    public void saveAll(java.util.Collection<Region> regions) {
        if (dao != null) dao.saveAll(regions);
    }

    public Map<UUID, org.qweyns.qweprotectstones.features.market.RegionSale> loadSales() {
        return dao == null ? Map.of() : dao.loadSales();
    }

    public Map<UUID, org.qweyns.qweprotectstones.features.market.RegionRental> loadRentals() {
        return dao == null ? Map.of() : dao.loadRentals();
    }

    // рынок пишется той же очередью, что и приваты: fire-and-forget задачи
    // терялись при выключении (cancelAll убивал их до записи в базу)

    public void saveSale(org.qweyns.qweprotectstones.features.market.RegionSale sale) {
        if (sale == null) return;
        pendingSaleDeletes.remove(sale.regionId());
        pendingSales.put(sale.regionId(), sale);
    }

    public void deleteSale(UUID regionId) {
        if (regionId == null) return;
        pendingSales.remove(regionId);
        pendingSaleDeletes.add(regionId);
    }

    public void saveRental(org.qweyns.qweprotectstones.features.market.RegionRental rental) {
        if (rental == null) return;
        pendingRentalDeletes.remove(rental.regionId());
        pendingRentals.put(rental.regionId(), rental);
    }

    public void deleteRental(UUID regionId) {
        if (regionId == null) return;
        pendingRentals.remove(regionId);
        pendingRentalDeletes.add(regionId);
    }

    public void close() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flush();
        if (dao != null) dao.close();
    }
}
