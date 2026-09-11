package org.qweyns.qweprotectstones.storage;

import org.qweyns.qweprotectstones.scheduler.Schedulers;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
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

/**
 * Асинхронная запись приватов в базу. Сам кэш живёт в {@code RegionManager},
 * здесь только очередь отложенных изменений, чтобы частые правки прочности
 * не били по диску на каждое попадание TNT.
 */
public class RegionStorage {

    private final QweProtectStones plugin;
    private RegionDao dao;
    private Schedulers.Task flushTask;

    private final Map<UUID, Region> pendingSaves = new ConcurrentHashMap<>();
    private final Set<UUID> pendingDeletes = ConcurrentHashMap.newKeySet();
    private final java.util.Queue<RegionLogEntry> pendingLog = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private volatile long lastFlushMillis;

    public RegionStorage(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    /** @return приваты, прочитанные из базы (реестр наполняет уже RegionManager) */
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
        long flushPeriod = plugin.getTunables().dbFlushTicks();
        flushTask = plugin.getSchedulers().runAsyncTimer(this::flush, flushPeriod, flushPeriod);
        return loaded;
    }

    /** Отложенная запись: несколько правок одного привата схлопываются в одну. */
    public void save(Region region) {
        if (region == null) return;
        pendingDeletes.remove(region.getId());
        pendingSaves.put(region.getId(), region);
    }

    /** Момент последней записи в базу (0 — ещё не было) — для /qps stats. */
    public long lastFlushMillis() {
        return lastFlushMillis;
    }

    /** Сколько приватов ждёт записи — для /qps debug. */
    public int pendingCount() {
        return pendingSaves.size() + pendingDeletes.size() + pendingLog.size();
    }

    /** Немедленная запись — после покупок, чтобы не потерять прогресс при краше. */
    public void saveNow(Region region) {
        if (region == null) return;
        pendingSaves.remove(region.getId());
        pendingDeletes.remove(region.getId());
        plugin.getSchedulers().runAsync(() -> dao.saveAll(List.of(region)));
    }

    public void delete(UUID regionId) {
        if (regionId == null) return;
        pendingSaves.remove(regionId);
        pendingDeletes.add(regionId);
    }

    public void loadAutoAddAsync(UUID uuid, BiConsumer<Set<String>, Boolean> callback) {
        plugin.getSchedulers().runAsync(() -> dao.loadAutoAdd(uuid, callback));
    }

    public void saveAutoAddAsync(UUID uuid, Set<String> friends, boolean toggledOff) {
        Set<String> snapshot = Set.copyOf(friends);
        plugin.getSchedulers().runAsync(() -> dao.saveAutoAdd(uuid, snapshot, toggledOff));
    }

    /** Синхронный вариант для выключения сервера, когда планировщик уже не работает. */
    public void saveAutoAddSync(UUID uuid, Set<String> friends, boolean toggledOff) {
        if (dao != null) dao.saveAutoAdd(uuid, friends, toggledOff);
    }

    // ------------------------------------------------------------------
    // Активность игроков и журнал
    // ------------------------------------------------------------------

    public void touchPlayerAsync(UUID uuid, String name) {
        long now = System.currentTimeMillis();
        plugin.getSchedulers().runAsync(() -> dao.touchPlayer(uuid, name, now));
    }

    public Map<UUID, Long> loadLastSeen() {
        return dao == null ? Map.of() : dao.loadLastSeen();
    }

    /** Журнал копится в памяти и уходит в базу пачкой вместе с остальным. */
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
            // Забираем по ключу: изменение во время флаша попадёт в следующий цикл, а не потеряется.
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
    }

    /** Полное сохранение всех приватов — используется при выключении и /region admin save. */
    public void saveAll(java.util.Collection<Region> regions) {
        if (dao != null) dao.saveAll(regions);
    }

    // ------------------------------------------------------------------
    // Рынок: продажа и аренда
    // ------------------------------------------------------------------

    /** Синхронно при старте: таблицы рынка маленькие, а данные нужны сразу. */
    public Map<UUID, org.qweyns.qweprotectstones.features.market.RegionSale> loadSales() {
        return dao == null ? Map.of() : dao.loadSales();
    }

    public Map<UUID, org.qweyns.qweprotectstones.features.market.RegionRental> loadRentals() {
        return dao == null ? Map.of() : dao.loadRentals();
    }

    /** Запись объявлений — сразу в базу: деньги уже уплачены, терять нельзя. */
    public void saveSaleNow(org.qweyns.qweprotectstones.features.market.RegionSale sale) {
        if (dao != null) plugin.getSchedulers().runAsync(() -> dao.saveSale(sale));
    }

    public void deleteSaleNow(UUID regionId) {
        if (dao != null) plugin.getSchedulers().runAsync(() -> dao.deleteSale(regionId));
    }

    public void saveRentalNow(org.qweyns.qweprotectstones.features.market.RegionRental rental) {
        if (dao != null) plugin.getSchedulers().runAsync(() -> dao.saveRental(rental));
    }

    public void deleteRentalNow(UUID regionId) {
        if (dao != null) plugin.getSchedulers().runAsync(() -> dao.deleteRental(regionId));
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
