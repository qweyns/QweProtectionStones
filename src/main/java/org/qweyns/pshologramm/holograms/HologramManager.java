package org.qweyns.pshologramm.holograms;

import dev.espi.protectionstones.PSRegion;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.models.RegionData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class HologramManager implements Listener {
    private final PSHologramm plugin;
    private IHologramProvider dhProvider;
    private IHologramProvider fhProvider;

    private final Map<String, List<String>> chunkCache = new ConcurrentHashMap<>();

    public HologramManager(PSHologramm plugin) {
        this.plugin = plugin;
    }

    public void init() {
        if (plugin.getServer().getPluginManager().isPluginEnabled("DecentHolograms")) {
            dhProvider = new DHProvider(plugin);
            plugin.getLogger().info("Мост для DecentHolograms загружен.");
        }
        if (plugin.getServer().getPluginManager().isPluginEnabled("FancyHolograms")) {
            fhProvider = new FHProvider(plugin);
            plugin.getLogger().info("Мост для FancyHolograms загружен.");
        }
        if (dhProvider == null && fhProvider == null) {
            Bukkit.getConsoleSender().sendMessage("§7[PSHologramm] ℹ Плагины на голограммы (DecentHolograms/FancyHolograms) не найдены. Голограммы приватов отключены.");
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void createOrUpdateHologram(String regionId, Location loc, String regionType, String owner, int dur, int maxDur) {
        if (!plugin.getConfigManager().isHologramEnabled(regionType)) {
            removeHologram(regionId);
            return;
        }

        String chunkKey = getChunkKey(loc);
        chunkCache.computeIfAbsent(chunkKey, k -> new CopyOnWriteArrayList<>()).add(regionId);

        if (loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            String type = plugin.getConfigManager().getHologramType(regionType);
            removeHologramVisual(regionId);

            if (type.equals("MODERN")) {
                if (fhProvider != null) fhProvider.createOrUpdate(regionId, loc, regionType, owner, dur, maxDur);
                else if (dhProvider != null) dhProvider.createOrUpdate(regionId, loc, regionType, owner, dur, maxDur);
            } else {
                if (dhProvider != null) dhProvider.createOrUpdate(regionId, loc, regionType, owner, dur, maxDur);
                else if (fhProvider != null) fhProvider.createOrUpdate(regionId, loc, regionType, owner, dur, maxDur);
            }
        }
    }

    public void removeHologram(String regionId) {
        for (List<String> list : chunkCache.values()) {
            list.remove(regionId);
        }
        removeHologramVisual(regionId);
    }

    private void removeHologramVisual(String regionId) {
        if (dhProvider != null) dhProvider.remove(regionId);
        if (fhProvider != null) fhProvider.remove(regionId);
    }

    public void deleteAll() {
        if (dhProvider != null) dhProvider.deleteAll();
        if (fhProvider != null) fhProvider.deleteAll();
        chunkCache.clear();
    }

    public void restoreHolograms() {
        deleteAll();

        List<String> invalidIds = new ArrayList<>();

        for (RegionData rd : plugin.getStorageManager().getAllRegions().values()) {
            World world = plugin.getServer().getWorld(rd.getWorld());
            if (world == null) continue;

            Location loc = new Location(world, rd.getX(), rd.getY(), rd.getZ());
            PSRegion psRegion = PSRegion.fromLocation(loc);

            if (psRegion == null || !psRegion.getId().equals(rd.getId())) {
                invalidIds.add(rd.getId());
                continue;
            }

            createOrUpdateHologram(rd.getId(), loc, rd.getType(), rd.getOwner(), rd.getDurability(), rd.getMaxDurability());
        }

        for (String id : invalidIds) {
            plugin.getStorageManager().removeRegion(id);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        String chunkKey = getChunkKey(event.getChunk());
        List<String> regionsInChunk = chunkCache.get(chunkKey);

        if (regionsInChunk != null && !regionsInChunk.isEmpty()) {
            for (String id : regionsInChunk) {
                RegionData rd = plugin.getStorageManager().getRegion(id);
                if (rd != null) {
                    World world = plugin.getServer().getWorld(rd.getWorld());
                    if (world != null) {
                        Location loc = new Location(world, rd.getX(), rd.getY(), rd.getZ());
                        createOrUpdateHologram(id, loc, rd.getType(), rd.getOwner(), rd.getDurability(), rd.getMaxDurability());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        String chunkKey = getChunkKey(event.getChunk());
        List<String> regionsInChunk = chunkCache.get(chunkKey);

        if (regionsInChunk != null && !regionsInChunk.isEmpty()) {
            for (String id : regionsInChunk) {
                removeHologramVisual(id);
            }
        }
    }

    private String getChunkKey(Location loc) {
        return loc.getWorld().getName() + ":" + (loc.getBlockX() >> 4) + ":" + (loc.getBlockZ() >> 4);
    }

    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
}
