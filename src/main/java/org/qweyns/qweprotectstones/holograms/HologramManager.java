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

    // ядро могли перенести — помним, в каком чанке чьё
    private final Map<UUID, String> regionChunk = new ConcurrentHashMap<>();

    private final QweProtectStones plugin;
    private IHologramProvider dhProvider;
    private IHologramProvider fhProvider;
    private IHologramProvider nativeProvider;

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
        // встроенный провайдер не требует ничего и всегда доступен
        nativeProvider = new NativeHologramProvider(plugin);
        if (dhProvider == null && fhProvider == null) {
            plugin.getLogger().info("DecentHolograms/FancyHolograms не найдены — использую встроенные голограммы (TextDisplay).");
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean isAvailable() {
        return nativeProvider != null || dhProvider != null || fhProvider != null;
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

        String chunkKey = chunkKey(core);
        String previousKey = regionChunk.put(region.getId(), chunkKey);
        if (previousKey != null && !previousKey.equals(chunkKey)) {
            Set<UUID> previousIds = chunkCache.get(previousKey);
            if (previousIds != null) {
                previousIds.remove(region.getId());
                if (previousIds.isEmpty()) chunkCache.remove(previousKey);
            }
        }
        chunkCache.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(region.getId());

        if (!core.getWorld().isChunkLoaded(core.getBlockX() >> 4, core.getBlockZ() >> 4)) return;

        // выбранный плагин может отсутствовать — тогда сработает встроенный
        IHologramProvider primary = switch (plugin.getConfigManager().getHologramType(region.getTypeId())) {
            case "NATIVE" -> nativeProvider;
            case "DECENT" -> firstNonNull(dhProvider, firstNonNull(fhProvider, nativeProvider));
            default -> firstNonNull(fhProvider, firstNonNull(dhProvider, nativeProvider));
        };
        if (primary == null) return;

        try {

            IHologramProvider other = primary == fhProvider ? dhProvider : fhProvider;
            if (other != null) other.remove(region.getId());

            primary.createOrUpdate(region, core);
        } catch (Throwable t) {
            // несовместимая версия плагина голограмм бросает Error — гасим провайдер с первого сбоя
            plugin.getLogger().log(Level.WARNING, "Провайдер голограмм упал, отключаю его (" + region.getShortId() + ")", t);
            if (primary == fhProvider) fhProvider = null;
            else dhProvider = null;
        }
    }

    private static IHologramProvider firstNonNull(IHologramProvider first, IHologramProvider second) {
        return first != null ? first : second;
    }

    public void removeHologram(UUID regionId) {
        regionChunk.remove(regionId);
        chunkCache.values().forEach(ids -> ids.remove(regionId));
        chunkCache.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        removeHologramVisual(regionId);
    }

    private void removeHologramVisual(UUID regionId) {
        try {
            if (dhProvider != null) dhProvider.remove(regionId);
            if (fhProvider != null) fhProvider.remove(regionId);
            if (nativeProvider != null) nativeProvider.remove(regionId);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Не удалось удалить голограмму привата " + regionId, t);
        }
    }

    public void deleteAll() {
        try {
            if (dhProvider != null) dhProvider.deleteAll();
            if (fhProvider != null) fhProvider.deleteAll();
            if (nativeProvider != null) nativeProvider.deleteAll();
        } catch (Throwable t) {

            plugin.getLogger().log(Level.WARNING, "Ошибка при удалении голограмм", t);
        }
        chunkCache.clear();
        regionChunk.clear();
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
