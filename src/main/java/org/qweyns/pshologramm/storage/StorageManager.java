package org.qweyns.pshologramm.storage;

import org.bukkit.Bukkit;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.models.RegionData;
import org.qweyns.pshologramm.storage.dao.MysqlDao;
import org.qweyns.pshologramm.storage.dao.RegionDao;
import org.qweyns.pshologramm.storage.dao.SqliteDao;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StorageManager {
    private final PSHologramm plugin;
    private RegionDao dao;

    private final Map<String, RegionData> cache = new ConcurrentHashMap<>();

    private final Set<RegionData> pendingSaves = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingDeletes = ConcurrentHashMap.newKeySet();

    public StorageManager(PSHologramm plugin) {
        this.plugin = plugin;
    }

    public void init() {
        String dbType = plugin.getConfigManager().getConfig().getString("database.type", "SQLITE").toUpperCase();
        dao = dbType.equals("MYSQL") ? new MysqlDao(plugin) : new SqliteDao(plugin);

        dao.init();
        cache.putAll(dao.loadAll());
        plugin.getLogger().info("Загружено " + cache.size() + " регионов из базы " + dbType + ".");

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushQueue, 60L, 60L);
    }

    public RegionData getRegion(String id) { return cache.get(id); }
    public Map<String, RegionData> getAllRegions() { return cache; }

    public void saveRegion(RegionData data) {
        cache.put(data.getId(), data);
        pendingSaves.add(data);
    }

    public void removeRegion(String id) {
        cache.remove(id);
        pendingSaves.removeIf(d -> d.getId().equals(id));
        pendingDeletes.add(id);
    }

    private synchronized void flushQueue() {
        if (!pendingSaves.isEmpty()) {
            List<RegionData> toSave = new ArrayList<>(pendingSaves);
            pendingSaves.clear();
            dao.saveAll(toSave);
        }
        if (!pendingDeletes.isEmpty()) {
            List<String> toDelete = new ArrayList<>(pendingDeletes);
            pendingDeletes.clear();
            dao.deleteAll(toDelete);
        }
    }

    public void close() {
        flushQueue();
        if (dao != null) dao.close();
    }
}
