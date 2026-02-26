package org.qweyns.pshologramm.storage.impl;

import org.bukkit.configuration.file.YamlConfiguration;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.models.RegionData;
import org.qweyns.pshologramm.storage.StorageProvider;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class YamlStorage implements StorageProvider {
    private final PSHologramm plugin;
    private final File file;
    private YamlConfiguration config;

    public YamlStorage(PSHologramm plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    @Override
    public void init() {
        if (!file.exists()) {
            try { file.getParentFile().mkdirs(); file.createNewFile(); } catch (Exception ignored) {}
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public Map<String, RegionData> loadAll() {
        Map<String, RegionData> map = new ConcurrentHashMap<>();
        if (config.getConfigurationSection("regions") == null) return map;
        for (String id : config.getConfigurationSection("regions").getKeys(false)) {
            String path = "regions." + id;
            map.put(id, new RegionData(
                    id, config.getString(path + ".type"), config.getString(path + ".owner"),
                    config.getString(path + ".material"), config.getInt(path + ".durability"),
                    config.getInt(path + ".maxDurability"), config.getString(path + ".location.world"),
                    config.getDouble(path + ".location.x"), config.getDouble(path + ".location.y"),
                    config.getDouble(path + ".location.z"), config.getStringList(path + ".effects")
            ));
        }
        return map;
    }

    @Override
    public synchronized void save(RegionData data) {
        String path = "regions." + data.getId();
        config.set(path + ".type", data.getType()); config.set(path + ".owner", data.getOwner());
        config.set(path + ".material", data.getMaterial()); config.set(path + ".durability", data.getDurability());
        config.set(path + ".maxDurability", data.getMaxDurability()); config.set(path + ".location.world", data.getWorld());
        config.set(path + ".location.x", data.getX()); config.set(path + ".location.y", data.getY());
        config.set(path + ".location.z", data.getZ()); config.set(path + ".effects", data.getEffects());
        try { config.save(file); } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public synchronized void remove(String id) {
        config.set("regions." + id, null);
        try { config.save(file); } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void close() { }
}
