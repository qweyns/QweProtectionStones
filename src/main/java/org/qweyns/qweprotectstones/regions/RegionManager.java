package org.qweyns.qweprotectstones.regions;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.event.RegionCreateEvent;
import org.qweyns.qweprotectstones.regions.event.RegionDeleteEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class RegionManager {

    public enum CreateStatus {
        SUCCESS,

        OVERLAP,

        TOO_CLOSE,

        LIMIT_REACHED,

        NO_PERMISSION,

        WORLD_DISABLED,

        CANCELLED
    }

    public record CreateResult(CreateStatus status, Region region, Region blockingRegion, int limit) {
        public static CreateResult success(Region region) {
            return new CreateResult(CreateStatus.SUCCESS, region, null, 0);
        }

        public static CreateResult failure(CreateStatus status) {
            return new CreateResult(status, null, null, 0);
        }

        public static CreateResult blocked(CreateStatus status, Region blockingRegion) {
            return new CreateResult(status, null, blockingRegion, 0);
        }

        public static CreateResult limited(int limit) {
            return new CreateResult(CreateStatus.LIMIT_REACHED, null, null, limit);
        }

        public boolean successful() {
            return status == CreateStatus.SUCCESS;
        }
    }

    private final QweProtectStones plugin;
    private final RegionIndex<Region> index = new RegionIndex<>();
    private final RegionIndex<Region> reservations = new RegionIndex<>();
    private final Map<UUID, Region> pendingCreates = new ConcurrentHashMap<>();

    private final Map<UUID, Region> regions = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> regionsByOwner = new ConcurrentHashMap<>();
    // префикс короткого id -> кто им владеет; skip-листа умеет искать по префиксу
    private final ConcurrentSkipListMap<String, List<UUID>> byShortId = new ConcurrentSkipListMap<>();

    public RegionManager(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public void loadAll(Collection<Region> loaded) {
        regions.clear();
        regionsByOwner.clear();
        byShortId.clear();
        index.clear();

        int skippedUnknownWorld = 0;
        for (Region region : loaded) {
            if (Bukkit.getWorld(region.getWorldName()) == null) {
                // мир может быть не подключён, приват ждёт в памяти

                skippedUnknownWorld++;
            }
            register(region);
        }

        if (skippedUnknownWorld > 0) {
            plugin.getLogger().info("Приватов в незагруженных мирах: " + skippedUnknownWorld + " (данные сохранены).");
        }
    }

    private void register(Region region) {
        regions.put(region.getId(), region);
        index.add(region);
        byShortId.computeIfAbsent(region.getShortId(), k -> new CopyOnWriteArrayList<>()).add(region.getId());
        if (region.getOwnerId() != null) {
            regionsByOwner.computeIfAbsent(region.getOwnerId(), k -> ConcurrentHashMap.newKeySet()).add(region.getId());
        }
    }

    private void unregister(Region region) {
        regions.remove(region.getId());
        index.remove(region);

        List<UUID> ids = byShortId.get(region.getShortId());
        if (ids != null) {
            ids.remove(region.getId());
            if (ids.isEmpty()) byShortId.remove(region.getShortId(), ids);
        }
    }

    public Region getRegionAt(Location loc) {
        if (loc == null) return null;
        World world = loc.getWorld();
        if (world == null) return null;
        return index.at(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public Region getRegionAt(World world, int x, int y, int z) {
        return world == null ? null : index.at(world.getName(), x, y, z);
    }

    public List<Region> intersecting(String world, RegionBounds bounds) {
        return index.intersecting(world, bounds);
    }

    public Region getById(UUID id) {
        return id == null ? null : regions.get(id);
    }

    public Region getByShortId(String shortId) {
        if (shortId == null || shortId.isBlank()) return null;
        String needle = shortId.toLowerCase(java.util.Locale.ROOT);

        // префикс длиннее индексного (полный UUID) — редкий путь, точный проход
        if (needle.length() > 8) {
            for (Region region : regions.values()) {
                if (region.getId().toString().startsWith(needle)) return region;
            }
            return null;
        }

        for (Map.Entry<String, List<UUID>> entry : byShortId.tailMap(needle).entrySet()) {
            if (!entry.getKey().startsWith(needle)) break;
            for (UUID id : entry.getValue()) {
                Region region = regions.get(id);
                if (region != null) return region;
            }
        }
        return null;
    }

    public List<Region> getRegionsOf(UUID ownerId) {
        Set<UUID> ids = regionsByOwner.get(ownerId);
        if (ids == null || ids.isEmpty()) return List.of();

        List<Region> result = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            Region region = regions.get(id);
            if (region != null) result.add(region);
        }
        return result;
    }

    public List<Region> getAccessibleRegions(UUID playerId) {
        List<Region> result = new ArrayList<>(getRegionsOf(playerId));
        for (Region region : regions.values()) {
            if (!region.isOwner(playerId) && region.getMember(playerId).isPresent()) result.add(region);
        }
        return result;
    }

    public int countRegionsOfType(UUID ownerId, String typeId) {
        int count = 0;
        for (Region region : getRegionsOf(ownerId)) {
            if (region.getTypeId().equals(typeId)) count++;
        }
        return count;
    }

    public Collection<Region> getAllRegions() {
        return List.copyOf(regions.values());
    }

    public int ownersCount() {
        java.util.Set<UUID> owners = new java.util.HashSet<>();
        for (Region region : getAllRegions()) {
            if (region.getOwnerId() != null) owners.add(region.getOwnerId());
        }
        return owners.size();
    }

    public int size() {
        return regions.size();
    }

    public CreateResult createRegion(Player owner, RegionType type, Location coreLocation) {
        CreateResult prepared = prepareCreation(owner, type, coreLocation);
        if (prepared.successful()) commitCreation(prepared.region());
        return prepared;
    }

    /** Резервация до конца BlockPlaceEvent: ещё не живой приват и не запись БД. */
    public CreateResult prepareCreation(Player owner, RegionType type, Location coreLocation) {
        World world = coreLocation.getWorld();
        if (world == null) return CreateResult.failure(CreateStatus.WORLD_DISABLED);

        if (!type.isWorldAllowed(world.getName().toLowerCase(java.util.Locale.ROOT))) {
            return CreateResult.failure(CreateStatus.WORLD_DISABLED);
        }
        // право на тип (place_permission) отдельно от общего create

        if (!owner.hasPermission(QweProtectStones.PERMISSION_PREFIX + ".create")) {
            return CreateResult.failure(CreateStatus.NO_PERMISSION);
        }
        if (!type.placePermission().isBlank() && !owner.hasPermission(type.placePermission())) {
            return CreateResult.failure(CreateStatus.NO_PERMISSION);
        }

        int limit = resolveLimit(owner, type);
        if (limit > 0 && countRegionsOfType(owner.getUniqueId(), type.id()) >= limit) {
            return CreateResult.limited(limit);
        }

        // full_height: территория от бедрока до неба, вне зависимости от radius_y.
        int radiusY = type.fullHeight() ? world.getMaxHeight() - world.getMinHeight() : type.radiusY();
        RegionBounds bounds = RegionBounds.around(
                coreLocation.getBlockX(), coreLocation.getBlockY(), coreLocation.getBlockZ(),
                type.radiusX(), radiusY, type.radiusZ(),
                world.getMinHeight(), world.getMaxHeight() - 1);

        Region overlapping = index.firstIntersecting(world.getName(), bounds);
        if (overlapping != null) return CreateResult.blocked(CreateStatus.OVERLAP, overlapping);

        if (type.minDistanceToOthers() > 0) {
            RegionBounds padded = bounds.expand(type.minDistanceToOthers());
            for (Region near : index.intersecting(world.getName(), padded)) {
                // Свои приваты можно ставить вплотную, чужие — нет.
                if (!near.isOwner(owner.getUniqueId())) return CreateResult.blocked(CreateStatus.TOO_CLOSE, near);
            }
        }

        Region region = new Region(
                UUID.randomUUID(), world.getName(), bounds,
                coreLocation.getBlockX(), coreLocation.getBlockY(), coreLocation.getBlockZ(),
                type.id(), owner.getUniqueId(), owner.getName(),
                type.startDurability(), type.maxDurability(), System.currentTimeMillis());

        synchronized (index) {
            int pending = (int) pendingCreates.values().stream().filter(r -> r.isOwner(owner.getUniqueId())
                    && r.getTypeId().equals(type.id())).count();
            if (limit > 0 && countRegionsOfType(owner.getUniqueId(), type.id()) + pending >= limit)
                return CreateResult.limited(limit);
            Region conflict = index.firstIntersecting(world.getName(), bounds);
            if (conflict == null) conflict = reservations.firstIntersecting(world.getName(), bounds);
            if (conflict != null) return CreateResult.blocked(CreateStatus.OVERLAP, conflict);
            if (type.minDistanceToOthers() > 0) {
                List<Region> near = new ArrayList<>(index.intersecting(world.getName(), bounds.expand(type.minDistanceToOthers())));
                near.addAll(reservations.intersecting(world.getName(), bounds.expand(type.minDistanceToOthers())));
                for (Region candidate : near) if (!candidate.isOwner(owner.getUniqueId()))
                    return CreateResult.blocked(CreateStatus.TOO_CLOSE, candidate);
            }
            pendingCreates.put(region.getId(), region);
            reservations.add(region);
        }
        try {
            RegionCreateEvent event = new RegionCreateEvent(region, owner);
            Bukkit.getPluginManager().callEvent(event);
            // Геометрия резервации неизменна: слушатель может отменить создание, но не
            // подменить границы/тип в обход проверок пересечения.
            if (event.isCancelled() || !bounds.equals(region.getBounds()) || !type.id().equals(region.getTypeId())
                    || !region.isCore(coreLocation) || !region.isOwner(owner.getUniqueId())) {
                abortCreation(region);
                return CreateResult.failure(CreateStatus.CANCELLED);
            }
            return CreateResult.success(region);
        } catch (RuntimeException | Error e) {
            abortCreation(region);
            throw e;
        }
    }

    public void abortCreation(Region region) {
        synchronized (index) {
            if (pendingCreates.remove(region.getId()) != null) {
                // Обработчик мог поменять bounds: очистка по всему индексу резерваций.
                reservations.clear();
                pendingCreates.values().forEach(reservations::add);
            }
        }
    }

    public void commitCreation(Region region) {
        synchronized (index) {
            if (!pendingCreates.containsKey(region.getId())) return;
            abortCreation(region);
            register(region);
        }
        plugin.getRegionStorage().save(region);
    }

    public final class PreparedDeletion implements AutoCloseable {
        private final Region region;
        private final Region.Operation operation;
        private PreparedDeletion(Region region, Region.Operation operation) {
            this.region = region; this.operation = operation;
        }
        public boolean commit() { return commitDeletion(region, operation); }
        @Override public void close() { operation.close(); }
    }

    public PreparedDeletion prepareDeletion(Region region, RegionDeleteEvent.Reason reason, Player actor) {
        if (region == null) return null;
        Region.Operation operation = region.tryOperation();
        if (operation == null) return null;
        try {
            if (!allowDeletion(region, reason, actor)) { operation.close(); return null; }
            return new PreparedDeletion(region, operation);
        } catch (RuntimeException | Error e) { operation.close(); throw e; }
    }

    private boolean allowDeletion(Region region, RegionDeleteEvent.Reason reason, Player actor) {
        if (regions.get(region.getId()) != region) return false;
        RegionDeleteEvent event = new RegionDeleteEvent(region, actor, reason);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled() && regions.get(region.getId()) == region;
    }

    public boolean deleteRegion(Region region, RegionDeleteEvent.Reason reason, Player actor) {
        try (PreparedDeletion deletion = prepareDeletion(region, reason, actor)) {
            return deletion != null && deletion.commit();
        }
    }

    /** Внутренняя часть осады: операция уже зарезервирована вызывающим. */
    public boolean deleteWithin(Region region, RegionDeleteEvent.Reason reason, Player actor, Region.Operation operation) {
        return operation != null && operation.owns(region) && allowDeletion(region, reason, actor)
                && commitDeletion(region, operation);
    }

    private boolean commitDeletion(Region region, Region.Operation operation) {
        if (!operation.owns(region)) return false;
        synchronized (index) {
            if (regions.get(region.getId()) != region) return false;
            unregister(region);
            Set<UUID> owned = regionsByOwner.get(region.getOwnerId());
            if (owned != null) owned.remove(region.getId());
        }
        if (plugin.getMarketManager() != null) plugin.getMarketManager().clearWithin(region, operation);
        plugin.getRegionStorage().delete(region.getId());
        return true;
    }

    public boolean importRegion(Region region) {
        if (region == null) return false;
        synchronized (index) {
            if (regions.containsKey(region.getId()) || index.firstIntersecting(region.getWorldName(), region.getBounds()) != null
                    || reservations.firstIntersecting(region.getWorldName(), region.getBounds()) != null) return false;
            register(region);
        }
        plugin.getRegionStorage().save(region);
        return true;
    }

    public void transferRegion(Region region, UUID newOwnerId, String newOwnerName) {
        transferRegion(region, newOwnerId, newOwnerName, null);
    }

    public boolean transferRegion(Region region, UUID newOwnerId, String newOwnerName, Player actor) {
        if (region == null || newOwnerId == null) return false;
        try (Region.Operation operation = region.tryOperation()) {
            if (operation == null || regions.get(region.getId()) != region) return false;
            if (org.qweyns.qweprotectstones.regions.event.RegionEvents.fireTransfer(region, actor, newOwnerId, newOwnerName)) return false;
            return transferWithin(region, newOwnerId, newOwnerName, operation);
        }
    }

    /** Фиксация после проверки события и (для покупки) успешной оплаты. */
    public boolean transferWithin(Region region, UUID newOwnerId, String newOwnerName, Region.Operation operation) {
        if (operation == null || !operation.owns(region) || newOwnerId == null) return false;
        synchronized (index) {
            if (regions.get(region.getId()) != region) return false;
            UUID previousOwner = region.getOwnerId();
            region.transferOwnership(newOwnerId, newOwnerName);
            Set<UUID> previous = previousOwner == null ? null : regionsByOwner.get(previousOwner);
            if (previous != null) previous.remove(region.getId());
            regionsByOwner.computeIfAbsent(newOwnerId, k -> ConcurrentHashMap.newKeySet()).add(region.getId());
        }
        if (plugin.getMarketManager() != null)
            plugin.getMarketManager().ownershipWithin(region, newOwnerId, newOwnerName, operation);
        plugin.getRegionStorage().save(region);
        return true;
    }

    public int resolveLimit(Player player, RegionType type) {
        if (player.hasPermission("qweprotectstones.limit." + type.id().toLowerCase(java.util.Locale.ROOT) + ".unlimited")
                || player.hasPermission("qweprotectstones.limit.unlimited")) {
            return 0;
        }

        int best = -1;
        String prefix = "qweprotectstones.limit." + type.id().toLowerCase(java.util.Locale.ROOT) + ".";
        for (var permission : player.getEffectivePermissions()) {
            if (!permission.getValue()) continue;

            String node = permission.getPermission().toLowerCase(java.util.Locale.ROOT);
            if (!node.startsWith(prefix)) continue;

            try {
                best = Math.max(best, Integer.parseInt(node.substring(prefix.length())));
            } catch (NumberFormatException ignored) {

            }
        }
        return best >= 0 ? best : type.maxPerPlayer();
    }

    public boolean isAreaFree(World world, RegionBounds bounds) {
        return world != null && index.firstIntersecting(world.getName(), bounds) == null;
    }

    public Region findOverlapping(World world, RegionBounds bounds) {
        return world == null ? null : index.firstIntersecting(world.getName(), bounds);
    }

    public Region updateBounds(Region region, RegionBounds newBounds) {
        if (region == null || newBounds == null) return region;
        try (Region.Operation operation = region.tryOperation()) {
            if (operation == null || regions.get(region.getId()) != region) return region;
            synchronized (index) {
                for (Region other : index.intersecting(region.getWorldName(), newBounds)) {
                    if (!other.getId().equals(region.getId())) return other;
                }
                Region reserved = reservations.firstIntersecting(region.getWorldName(), newBounds);
                if (reserved != null) return reserved;
                index.remove(region);
                region.setBounds(newBounds);
                index.add(region);
            }
            plugin.getRegionStorage().save(region);
            return null;
        }
    }

    /** Ядро переехало (поршень при protection.pistons.move-core). Границы привата не меняются. */
    public void updateCore(Region region, int x, int y, int z) {
        region.setCore(x, y, z);
        region.touch();
        plugin.getRegionStorage().save(region);
        plugin.getHologramManager().createOrUpdateHologram(region);
        plugin.getDynmapIntegration().update(region);
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().update(region);
    }

    public Region reapplyTypeBounds(Region region, RegionType type) {
        World world = region.getWorld();
        if (world == null) return null;

        int radiusY = type.fullHeight() ? world.getMaxHeight() - world.getMinHeight() : type.radiusY();
        RegionBounds bounds = RegionBounds.around(
                region.getCoreX(), region.getCoreY(), region.getCoreZ(),
                type.radiusX(), radiusY, type.radiusZ(),
                world.getMinHeight(), world.getMaxHeight() - 1);
        return updateBounds(region, bounds);
    }


    public void refreshTypeData() {
        for (Region region : regions.values()) {
            RegionType type = plugin.getRegionTypes().resolveOrFallback(region.getTypeId());
            if (type != null) region.setMaxDurability(type.maxDurability());
        }
    }
}
