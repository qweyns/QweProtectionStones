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
    // Читается и меняется под монитором конвейера записи.
    private boolean closed;
    private boolean recoveryBlocked;

    private final RegionWriteQueue regions = new RegionWriteQueue();
    private final RetryQueue<UUID> sales = new RetryQueue<>();
    private final RetryQueue<UUID> rentals = new RetryQueue<>();
    private final RetryQueue<UUID> autoadd = new RetryQueue<>();
    private final RetryQueue<UUID> players = new RetryQueue<>();
    private final LogWriteQueue logs = new LogWriteQueue();
    private volatile long lastFlushMillis;

    public RegionStorage(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public List<Region> init() {
        recoveryBlocked = true;
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
        try { StorageRecovery.replay(recoveryPath(), dao); }
        catch (java.io.IOException e) { throw new IllegalStateException("Журнал восстановления не применён; запуск запрещён", e); }
        List<Region> loaded = dao.loadAll();
        recoveryBlocked = false;

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
        regions.save(region, this::isLive);
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
        return regions.size() + sales.size() + rentals.size() + autoadd.size() + players.size() + logs.size();
    }

    public void saveNow(Region region) {
        if (region == null || !isLive(region)) return;
        // Срочная запись идёт тем же конвейером: иначе она могла обогнать
        // удаление и воскресить приват в БД после завершения flush().
        save(region);
        plugin.getSchedulers().runAsync(this::flush);
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
            save(region);
            count++;
        }
        flush();
        return count;
    }

    public void delete(UUID regionId) {
        if (regionId == null) return;
        regions.delete(regionId);
    }

    public void loadAutoAddAsync(UUID uuid, BiConsumer<Set<String>, Boolean> callback) {
        loadAutoAddAsync(uuid, callback, () -> { });
    }

    public void loadAutoAddAsync(UUID uuid, BiConsumer<Set<String>, Boolean> callback, Runnable failed) {
        plugin.getSchedulers().runAsync(() -> {
            synchronized (this) {
                if (closed || dao == null) return;
                flush();
                if (autoadd.size() != 0) { failed.run(); return; }
                try { dao.loadAutoAdd(uuid, callback); }
                catch (RuntimeException e) { writeFailed(e); failed.run(); }
            }
        });
    }

    public void saveAutoAddAsync(UUID uuid, Set<String> friends, boolean toggledOff) {
        Set<String> snapshot = Set.copyOf(friends);
        autoadd.put(uuid, () -> dao.saveAutoAdd(uuid, snapshot, toggledOff),
                StorageRecovery.command("autoadd",uuid,"friends",List.copyOf(snapshot),"off",toggledOff));
    }

    public synchronized void saveAutoAddSync(UUID uuid, Set<String> friends, boolean toggledOff) {
        saveAutoAddAsync(uuid, friends, toggledOff);
        flush();
    }

    public void touchPlayerAsync(UUID uuid, String name) {
        long now = System.currentTimeMillis();
        players.put(uuid, () -> dao.touchPlayer(uuid, name, now), StorageRecovery.command("player",uuid,"name",name,"at",now));
    }

    public synchronized Map<UUID, Long> loadLastSeen() {
        return dao == null || closed ? Map.of() : dao.loadLastSeen();
    }

    public void log(RegionLogEntry entry) {
        if (entry != null) logs.add(entry);
    }

    public void readLogAsync(UUID regionId, int limit, java.util.function.Consumer<List<RegionLogEntry>> callback) {
        plugin.getSchedulers().runAsync(() -> {
            List<RegionLogEntry> entries;
            synchronized (this) {
                if (dao == null || closed) return;
                entries = dao.readLog(regionId, limit);
            }
            plugin.getSchedulers().runNextTick(() -> callback.accept(entries));
        });
    }

    public synchronized int pruneLog(long olderThan) {
        return dao == null || closed ? 0 : dao.pruneLog(olderThan);
    }

    private void writeFailed(RuntimeException failure) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Запись БД не подтверждена; операция оставлена для повтора", failure);
    }

    private synchronized void flush() {
        if (dao == null || closed || recoveryBlocked) return;
        lastFlushMillis = System.currentTimeMillis();

        // Если остался аварийный журнал, сначала обновляем его: старое сохранение
        // после рестарта не должно отменить более новое успешное удаление.
        if (plugin.getDataFolder() != null && java.nio.file.Files.exists(recoveryPath()) && !checkpoint()) return;
        regions.flush(dao, this::isLive, this::writeFailed);
        sales.flush(this::writeFailed);
        rentals.flush(this::writeFailed);
        autoadd.flush(this::writeFailed);
        players.flush(this::writeFailed);
        logs.flush(dao,this::writeFailed);
        checkpoint();
    }

    private java.nio.file.Path recoveryPath() {
        return plugin.getDataFolder().toPath().resolve("storage-recovery.yml");
    }

    private boolean checkpoint() {
        // JavaPlugin всегда имеет dataFolder; null допустим только в unit-моке.
        if (plugin.getDataFolder() == null) return true;
        List<Map<String,Object>> operations = new ArrayList<>(regions.recovery(this::isLive));
        operations.addAll(sales.recovery()); operations.addAll(rentals.recovery());
        operations.addAll(autoadd.recovery()); operations.addAll(players.recovery()); operations.addAll(logs.recovery());
        try { StorageRecovery.write(recoveryPath(),operations); return true; }
        catch (java.io.IOException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"Не удалось записать аварийный журнал БД",e);
            return false;
        }
    }

    public synchronized void saveAll(java.util.Collection<Region> regions) {
        if (dao != null && !closed) saveSnapshot(regions);
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
        sales.put(sale.regionId(), () -> dao.saveSale(sale), StorageRecovery.command("sale",sale.regionId(),
                "owner",sale.sellerId().toString(),"name",sale.sellerName(),"price",sale.price(),"at",sale.createdAt()));
    }

    public void deleteSale(UUID regionId) {
        if (regionId != null) sales.put(regionId, () -> dao.deleteSale(regionId), StorageRecovery.command("deleteSale",regionId));
    }

    public void saveRental(org.qweyns.qweprotectstones.features.market.RegionRental rental) {
        if (rental != null) rentals.put(rental.regionId(), () -> dao.saveRental(rental), StorageRecovery.command("rental",rental.regionId(),
                "owner",rental.ownerId().toString(),"name",rental.ownerName(),"price",rental.price(),"minutes",rental.durationMinutes(),
                "tenant",rental.tenantId() == null ? "" : rental.tenantId().toString(),"tenantName",rental.tenantName(),"until",rental.rentedUntil()));
    }

    public void deleteRental(UUID regionId) {
        if (regionId != null) rentals.put(regionId, () -> dao.deleteRental(regionId), StorageRecovery.command("deleteRental",regionId));
    }

    public synchronized void close() {
        if (closed) return;
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flush();
        if (pendingCount() > 0) plugin.getLogger().severe("Остановка с неподтверждёнными записями: "
                + pendingCount() + ". Проверьте storage-recovery.yml и сообщения о его записи; он применяется до загрузки приватов.");
        if (dao != null) {
            dao.close();
            closed = true;
        }
    }
}
