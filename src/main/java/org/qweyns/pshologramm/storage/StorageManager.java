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
    private final Map<String, Set<RegionData>> chunkCache = new ConcurrentHashMap<>();

    private final Set<RegionData> pendingSaves = new HashSet<>();
    private final Set<String> pendingDeletes = new HashSet<>();

    public StorageManager(PSHologramm plugin) { this.plugin = plugin; }

    public void init() {
        String dbType = plugin.getConfigManager().getConfig().getString("database.type", "SQLITE").toUpperCase();
        dao = dbType.equals("MYSQL") ? new MysqlDao(plugin) : new SqliteDao(plugin);

        dao.init();
        cache.putAll(dao.loadAll());
        for (RegionData rd : cache.values()) addToChunkCache(rd);

        plugin.getLogger().info("Загружено " + cache.size() + " регионов из базы " + dbType + ".");
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushQueue, 60L, 60L);
    }

    public RegionData getRegion(String id) { return cache.get(id); }
    public Map<String, RegionData> getAllRegions() { return cache; }

    public Set<RegionData> getRegionsInChunk(String world, int chunkX, int chunkZ) {
        return chunkCache.getOrDefault(world + ":" + chunkX + ":" + chunkZ, Collections.emptySet());
    }

    public void saveRegion(RegionData data) {
        removeFromChunkCache(data.getId());
        cache.put(data.getId(), data);
        addToChunkCache(data);
        synchronized (pendingSaves) { pendingSaves.add(data); }
    }

    public void forceSave(RegionData data) {
        saveRegion(data);
        synchronized (pendingSaves) { pendingSaves.remove(data); }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> dao.saveAll(Collections.singletonList(data)));
    }

    public void removeRegion(String id) {
        removeFromChunkCache(id);
        cache.remove(id);
        synchronized (pendingSaves) { pendingSaves.removeIf(d -> d.getId().equals(id)); }
        synchronized (pendingDeletes) { pendingDeletes.add(id); }
    }

    private void addToChunkCache(RegionData data) {
        if (data.getWorld() == null) return;
        int chunkX = (int) data.getX() >> 4;
        int chunkZ = (int) data.getZ() >> 4;
        chunkCache.computeIfAbsent(data.getWorld() + ":" + chunkX + ":" + chunkZ, k -> ConcurrentHashMap.newKeySet()).add(data);
    }

    private void removeFromChunkCache(String id) {
        RegionData data = cache.get(id);
        if (data != null && data.getWorld() != null) {
            int chunkX = (int) data.getX() >> 4;
            int chunkZ = (int) data.getZ() >> 4;
            Set<RegionData> set = chunkCache.get(data.getWorld() + ":" + chunkX + ":" + chunkZ);
            if (set != null) set.remove(data);
        }
    }

    public void loadAutoAddAsync(UUID uuid, java.util.function.BiConsumer<Set<String>, Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> dao.loadAutoAdd(uuid, callback));
    }

    public void saveAutoAddAsync(UUID uuid, Set<String> friends, boolean toggledOff) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> dao.saveAutoAdd(uuid, friends, toggledOff));
    }

    public void saveAutoAddSync(UUID uuid, Set<String> friends, boolean toggledOff) {
        if (dao != null) {
            dao.saveAutoAdd(uuid, friends, toggledOff);
        }
    }

    private void flushQueue() {
        List<RegionData> toSave = new ArrayList<>();
        List<String> toDelete = new ArrayList<>();

        synchronized (pendingSaves) {
            if (!pendingSaves.isEmpty()) {
                toSave.addAll(pendingSaves);
                pendingSaves.clear();
            }
        }
        synchronized (pendingDeletes) {
            if (!pendingDeletes.isEmpty()) {
                toDelete.addAll(pendingDeletes);
                pendingDeletes.clear();
            }
        }

        if (!toSave.isEmpty()) dao.saveAll(toSave);
        if (!toDelete.isEmpty()) dao.deleteAll(toDelete);
    }

    public void close() {
        flushQueue();
        if (dao != null) dao.close();
    }
}
