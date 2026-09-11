package org.qweyns.qweprotectstones.holograms;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class HologramManager implements Listener {

    private final QweProtectStones plugin;
    private IHologramProvider dhProvider;
    private IHologramProvider fhProvider;

    private final Map<String, Set<UUID>> chunkCache = new ConcurrentHashMap<>();

    public HologramManager(QweProtectStones plugin) {
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
            plugin.getLogger().info("Плагины на голограммы не найдены — голограммы приватов отключены.");
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean isAvailable() {
        return dhProvider != null || fhProvider != null;
    }

    public void createOrUpdateHologram(Region region) {
        if (region == null) return;

        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        if (type != null && !type.hologramEnabled()) {
            removeHologram(region.getId());
            return;
        }

        Location core = region.getCoreLocation();
        if (core == null || core.getWorld() == null) return;

        chunkCache.computeIfAbsent(chunkKey(core), k -> ConcurrentHashMap.newKeySet()).add(region.getId());

        if (!core.getWorld().isChunkLoaded(core.getBlockX() >> 4, core.getBlockZ() >> 4)) return;

        boolean modern = plugin.getConfigManager().getHologramType(region.getTypeId()).equals("MODERN");
        IHologramProvider primary = modern ? firstNonNull(fhProvider, dhProvider) : firstNonNull(dhProvider, fhProvider);
        if (primary == null) return;

        try {

            IHologramProvider other = primary == fhProvider ? dhProvider : fhProvider;
            if (other != null) other.remove(region.getId());

            primary.createOrUpdate(region, core);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось обновить голограмму привата " + region.getShortId(), e);
        }
    }

    private static IHologramProvider firstNonNull(IHologramProvider first, IHologramProvider second) {
        return first != null ? first : second;
    }

    public void removeHologram(UUID regionId) {
        chunkCache.values().forEach(ids -> ids.remove(regionId));
        chunkCache.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        removeHologramVisual(regionId);
    }

    private void removeHologramVisual(UUID regionId) {
        try {
            if (dhProvider != null) dhProvider.remove(regionId);
            if (fhProvider != null) fhProvider.remove(regionId);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось удалить голограмму привата " + regionId, e);
        }
    }

    public void deleteAll() {
        try {
            if (dhProvider != null) dhProvider.deleteAll();
            if (fhProvider != null) fhProvider.deleteAll();
        } catch (Exception e) {

            plugin.getLogger().log(Level.WARNING, "Ошибка при удалении голограмм", e);
        }
        chunkCache.clear();
    }

    public void restoreHolograms() {
        deleteAll();

        for (Region region : plugin.getRegionManager().getAllRegions()) {
            World world = Bukkit.getWorld(region.getWorldName());
            if (world == null) continue;

            createOrUpdateHologram(region);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Set<UUID> regionIds = chunkCache.get(chunkKey(event.getChunk()));
        if (regionIds == null || regionIds.isEmpty()) return;

        List<UUID> snapshot = List.copyOf(regionIds);
        // создание в потоке региона, на Folia обязательно
        Location anchor = event.getChunk().getBlock(0, 0, 0).getLocation();
        plugin.getSchedulers().runAtLocationLater(anchor, () -> {
            for (UUID id : snapshot) {
                Region region = plugin.getRegionManager().getById(id);
                if (region != null) createOrUpdateHologram(region);
            }
        }, 1L);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Set<UUID> regionIds = chunkCache.get(chunkKey(event.getChunk()));
        if (regionIds == null) return;

        regionIds.forEach(this::removeHologramVisual);
    }

    private static String chunkKey(Location loc) {
        return loc.getWorld().getName() + ":" + (loc.getBlockX() >> 4) + ":" + (loc.getBlockZ() >> 4);
    }

    private static String chunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
}
