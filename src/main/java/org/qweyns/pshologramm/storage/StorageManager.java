package org.qweyns.pshologramm.storage;

import org.bukkit.Bukkit;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.models.RegionData;
import org.qweyns.pshologramm.storage.impl.SqlStorage;
import org.qweyns.pshologramm.storage.impl.YamlStorage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StorageManager {
    private final PSHologramm plugin;
    private StorageProvider provider;

    private final Map<String, RegionData> cache = new ConcurrentHashMap<>();

    public StorageManager(PSHologramm plugin) {
        this.plugin = plugin;
    }

    public void init() {
        String dbType = plugin.getConfigManager().getConfig().getString("database.type", "YAML").toUpperCase();

        if (dbType.equals("MYSQL") || dbType.equals("SQLITE") || dbType.equals("H2")) {
            provider = new SqlStorage(plugin, dbType);
        } else {
            provider = new YamlStorage(plugin);
        }

        provider.init();
        cache.putAll(provider.loadAll());

        plugin.getLogger().info("Загружено " + cache.size() + " регионов из хранилища (" + dbType + ").");
    }

    public RegionData getRegion(String id) {
        return cache.get(id);
    }

    public Map<String, RegionData> getAllRegions() {
        return cache;
    }

    public void saveRegion(RegionData data) {
        cache.put(data.getId(), data);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> provider.save(data));
    }

    public void removeRegion(String id) {
        cache.remove(id);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> provider.remove(id));
    }

    public void close() {
        if (provider != null) {
            provider.close();
        }
    }
}
