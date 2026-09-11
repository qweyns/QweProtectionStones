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

/**
 * Реестр всех приватов сервера: создание, удаление, поиск и проверка лимитов.
 * Заменяет собой обращения к ProtectionStones и WorldGuard.
 */
public class RegionManager {

    /** Результат попытки создать приват. */
    public enum CreateStatus {
        SUCCESS,
        /** Территория пересекается с чужим приватом. */
        OVERLAP,
        /** Слишком близко к чужому привату (min_distance_to_others). */
        TOO_CLOSE,
        /** Достигнут лимит приватов этого типа. */
        LIMIT_REACHED,
        /** Нет права на установку этого блока. */
        NO_PERMISSION,
        /** Тип запрещён в этом мире. */
        WORLD_DISABLED,
        /** Создание отменено другим плагином через RegionCreateEvent. */
        CANCELLED
    }

    /** Итог создания: статус плюс контекст для сообщения игроку. */
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

    private final Map<UUID, Region> regions = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> regionsByOwner = new ConcurrentHashMap<>();

    public RegionManager(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Загрузка
    // ------------------------------------------------------------------

    /** Заполняет реестр данными из базы (вызывается один раз при запуске). */
    public void loadAll(Collection<Region> loaded) {
        regions.clear();
        regionsByOwner.clear();
        index.clear();

        int skippedUnknownWorld = 0;
        for (Region region : loaded) {
            if (Bukkit.getWorld(region.getWorldName()) == null) {
                // Мир может быть просто не подключён (мультиверс) — приват
                // остаётся в памяти и в индексе: когда мир подключат, защита
                // заработает сама, без перезапуска.
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
        if (region.getOwnerId() != null) {
            regionsByOwner.computeIfAbsent(region.getOwnerId(), k -> ConcurrentHashMap.newKeySet()).add(region.getId());
        }
    }

    // ------------------------------------------------------------------
    // Поиск
    // ------------------------------------------------------------------

    /** Приват в этой точке или {@code null}. Основной метод горячего пути. */
    public Region getRegionAt(Location loc) {
        if (loc == null) return null;
        World world = loc.getWorld();
        if (world == null) return null;
        return index.at(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public Region getRegionAt(World world, int x, int y, int z) {
        return world == null ? null : index.at(world.getName(), x, y, z);
    }

    public Region getById(UUID id) {
        return id == null ? null : regions.get(id);
    }

    /** Поиск по началу короткого идентификатора — для админских команд. */
    public Region getByShortId(String shortId) {
        if (shortId == null || shortId.isBlank()) return null;
        String needle = shortId.toLowerCase(java.util.Locale.ROOT);

        for (Region region : regions.values()) {
            if (region.getId().toString().toLowerCase(java.util.Locale.ROOT).startsWith(needle)) return region;
        }
        return null;
    }

    /** Приват, чьё ядро стоит именно в этой точке. */

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

    /** Приваты, где игрок владелец или участник, — для /region list. */
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
        return Collections.unmodifiableCollection(regions.values());
    }


    public int size() {
        return regions.size();
    }

    // ------------------------------------------------------------------
    // Создание и удаление
    // ------------------------------------------------------------------

    /**
     * Проверяет все условия и создаёт приват. Вызывается из обработчика
     * установки блока, поэтому ничего не делает при неуспехе.
     */
    public CreateResult createRegion(Player owner, RegionType type, Location coreLocation) {
        World world = coreLocation.getWorld();
        if (world == null) return CreateResult.failure(CreateStatus.WORLD_DISABLED);

        if (!type.isWorldAllowed(world.getName().toLowerCase(java.util.Locale.ROOT))) {
            return CreateResult.failure(CreateStatus.WORLD_DISABLED);
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

        RegionCreateEvent event = new RegionCreateEvent(region, owner);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return CreateResult.failure(CreateStatus.CANCELLED);

        register(region);
        plugin.getRegionStorage().save(region);
        return CreateResult.success(region);
    }

    /**
     * @param reason причина удаления, попадает в {@link RegionDeleteEvent}
     * @return false, если удаление отменено другим плагином
     */
    public boolean deleteRegion(Region region, RegionDeleteEvent.Reason reason, Player actor) {
        if (region == null || !regions.containsKey(region.getId())) return false;

        RegionDeleteEvent event = new RegionDeleteEvent(region, actor, reason);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        regions.remove(region.getId());
        index.remove(region);

        Set<UUID> owned = regionsByOwner.get(region.getOwnerId());
        if (owned != null) {
            owned.remove(region.getId());
            if (owned.isEmpty()) regionsByOwner.remove(region.getOwnerId(), owned);
        }

        plugin.getRegionStorage().delete(region.getId());
        // Штраф больше не имеет смысла: привата нет, а UUID нового никогда не совпадёт.
        plugin.getPenaltyManager().removeRegion(region.getId());
        // Объявления рынка тоже теряют смысл без привата.
        if (plugin.getMarketManager() != null) {
            plugin.getMarketManager().cancelSale(region);
            plugin.getMarketManager().cancelRental(region);
        }
        return true;
    }

    /**
     * Импорт чужого региона (WorldGuard / ProtectionStones / GriefPrevention):
     * без проверок лимитов и прав — только контроль пересечений с нашими
     * приватами. Ядро виртуальное: bounds не обязаны быть симметричны вокруг него.
     *
     * @return true, если регион принят
     */
    public boolean importRegion(Region region) {
        if (region == null) return false;
        if (findOverlapping(region.getWorld(), region.getBounds()) != null) return false;

        register(region);
        plugin.getRegionStorage().save(region);
        return true;
    }

    /** Меняет владельца и переносит приват между индексами. */
    public void transferRegion(Region region, UUID newOwnerId, String newOwnerName) {
        UUID previousOwner = region.getOwnerId();

        region.transferOwnership(newOwnerId, newOwnerName);

        Set<UUID> previous = regionsByOwner.get(previousOwner);
        if (previous != null) previous.remove(region.getId());
        regionsByOwner.computeIfAbsent(newOwnerId, k -> ConcurrentHashMap.newKeySet()).add(region.getId());

        plugin.getRegionStorage().save(region);
    }

    /**
     * Лимит приватов данного типа: значение из permission вида
     * {@code qweprotectstones.limit.<тип>.<число>} перекрывает настройку типа.
     */
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
                // Узел не с числом на конце — не наш формат.
            }
        }
        return best >= 0 ? best : type.maxPerPlayer();
    }

    /** Свободна ли область: используется поиском места и предпросмотром границ. */
    public boolean isAreaFree(World world, RegionBounds bounds) {
        return world != null && index.firstIntersecting(world.getName(), bounds) == null;
    }

    /** Приват, мешающий занять область, или null. */
    public Region findOverlapping(World world, RegionBounds bounds) {
        return world == null ? null : index.firstIntersecting(world.getName(), bounds);
    }

    /**
     * Меняет границы существующего привата (/ps expand, /ps move, setbounds).
     * Сам приват пересечением не считается. Пересобирает чанковый индекс
     * и сохраняет регион в базу.
     *
     * @return приватов-нарушитель, если новые границы пересекают чужую область, иначе null
     */
    public Region updateBounds(Region region, RegionBounds newBounds) {
        if (region == null || newBounds == null) return null;

        for (Region other : index.intersecting(region.getWorldName(), newBounds)) {
            if (!other.getId().equals(region.getId())) return other;
        }

        // Сначала убираем из индекса по СТАРЫМ границам, потом меняем и добавляем заново.
        index.remove(region);
        region.setBounds(newBounds);
        index.add(region);

        plugin.getRegionStorage().save(region);
        return null;
    }

    /**
     * Пересчитывает границы привата по радиусам типа (используется при смене типа).
     *
     * @return приватов-нарушитель или null при успехе
     */
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

    /**
     * Переносит ядро привата в новую точку (/ps move): переставляет блок ядра,
     * центрирует границы вокруг нового ядра и пересобирает индекс.
     *
     * @return приватов-нарушитель на новом месте или null при успехе
     */
    public Region moveRegion(Region region, org.bukkit.Location newCore) {
        World world = newCore.getWorld();
        if (world == null || !world.getName().equals(region.getWorldName())) return null;

        RegionType type = plugin.getRegionTypes().resolveOrFallback(region.getTypeId());
        if (type == null) {
            // Без типов regions.yml переносить нечего: не из чего взять радиусы.
            plugin.getLogger().warning("Перенос привата " + region.getShortId() + " невозможен: типы не настроены.");
            return null;
        }
        int radiusY = type.fullHeight() ? world.getMaxHeight() - world.getMinHeight() : type.radiusY();
        RegionBounds newBounds = RegionBounds.around(
                newCore.getBlockX(), newCore.getBlockY(), newCore.getBlockZ(),
                type.radiusX(), radiusY, type.radiusZ(),
                world.getMinHeight(), world.getMaxHeight() - 1);

        for (Region other : index.intersecting(world.getName(), newBounds)) {
            if (!other.getId().equals(region.getId())) return other;
        }

        index.remove(region);

        // Блок ядра переносится: старое место очищаем, на новом ставим материал типа.
        org.bukkit.block.Block oldCore = world.getBlockAt(region.getCoreX(), region.getCoreY(), region.getCoreZ());
        if (oldCore.getType() == type.material()) oldCore.setType(org.bukkit.Material.AIR);
        world.getBlockAt(newCore.getBlockX(), newCore.getBlockY(), newCore.getBlockZ()).setType(type.material());

        region.setCore(newCore.getBlockX(), newCore.getBlockY(), newCore.getBlockZ());
        region.setBounds(newBounds);
        index.add(region);

        plugin.getRegionStorage().save(region);
        return null;
    }

    /** Пересчитывает максимум прочности у всех приватов после правки config.yml. */
    public void refreshTypeData() {
        for (Region region : regions.values()) {
            RegionType type = plugin.getRegionTypes().resolveOrFallback(region.getTypeId());
            if (type != null) region.setMaxDurability(type.maxDurability());
        }
    }
}
