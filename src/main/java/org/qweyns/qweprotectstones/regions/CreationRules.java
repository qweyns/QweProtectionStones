package org.qweyns.qweprotectstones.regions;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.config.MaterialPatterns;
import org.qweyns.qweprotectstones.regions.event.RegionDeleteEvent;
import org.qweyns.qweprotectstones.regions.event.RegionDeletedEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Антиабуз при установке привата (секция {@code creation} в protection.yml):
 * пауза после сноса своего привата, запрет ставить приват рядом с чужой осадой,
 * у точки спавна мира и поверх порталов и других заданных блоков.
 * Право {@code qweprotectstones.bypass.creation-rules} снимает все эти ограничения.
 */
public final class CreationRules implements Listener {

    public static final String BYPASS = QweProtectStones.PERMISSION_PREFIX + ".bypass.creation-rules";

    public enum Denial { COOLDOWN, NEAR_SIEGE, NEAR_SPAWN, FORBIDDEN_BLOCK }

    /** Результат проверки: причина отказа и подробность для сообщения (секунды, блок, радиус). */
    public record Result(Denial denial, String detail) { }

    private record Settings(long cooldownMs, int siegeRadius, int spawnRadius, int blockRadius, Set<Material> blocks) { }

    private final QweProtectStones plugin;
    private final Map<UUID, Long> lastRemoval = new ConcurrentHashMap<>();
    private volatile Settings settings;

    public CreationRules(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public void reload() { settings = null; }

    private Settings settings() {
        Settings current = settings;
        if (current != null) return current;
        var cfg = plugin.getConfigManager().getConfig();
        List<String> raw = cfg.contains("creation.forbidden-blocks")
                ? cfg.getStringList("creation.forbidden-blocks")
                : List.of("NETHER_PORTAL", "END_PORTAL", "END_PORTAL_FRAME", "END_GATEWAY");
        Set<Material> blocks = MaterialPatterns.materials(raw,
                bad -> plugin.getLogger().warning("creation.forbidden-blocks: неизвестный материал или тег '" + bad + "'"));
        current = new Settings(
                Math.max(0, cfg.getLong("creation.recreate-cooldown-seconds", 0)) * 1000L,
                Math.max(0, cfg.getInt("creation.near-siege-radius", 48)),
                Math.max(0, cfg.getInt("creation.spawn-radius", 0)),
                Math.max(0, Math.min(32, cfg.getInt("creation.forbidden-blocks-radius", 4))),
                blocks);
        settings = current;
        return current;
    }

    /** null — ставить можно. */
    public Result check(Player owner, Location core, RegionBounds bounds) {
        if (owner.hasPermission(BYPASS)) return null;
        Settings s = settings();
        World world = core.getWorld();
        if (world == null) return null;

        if (s.cooldownMs() > 0) {
            Long removed = lastRemoval.get(owner.getUniqueId());
            long left = removed == null ? 0 : removed + s.cooldownMs() - System.currentTimeMillis();
            if (left > 0) return new Result(Denial.COOLDOWN, String.valueOf((left + 999) / 1000));
        }

        if (s.siegeRadius() > 0) {
            for (Region near : plugin.getRegionManager().intersecting(world.getName(), bounds.expand(s.siegeRadius()))) {
                if (!near.isOwner(owner.getUniqueId()) && plugin.isUnderSiege(near)) {
                    return new Result(Denial.NEAR_SIEGE, String.valueOf(s.siegeRadius()));
                }
            }
        }

        if (s.spawnRadius() > 0) {
            Location spawn = world.getSpawnLocation();
            int r = s.spawnRadius();
            // расстояние от точки спавна до ближайшей точки территории (по горизонтали)
            int dx = Math.max(0, Math.max(bounds.minX() - spawn.getBlockX(), spawn.getBlockX() - bounds.maxX()));
            int dz = Math.max(0, Math.max(bounds.minZ() - spawn.getBlockZ(), spawn.getBlockZ() - bounds.maxZ()));
            if ((long) dx * dx + (long) dz * dz <= (long) r * r) return new Result(Denial.NEAR_SPAWN, String.valueOf(r));
        }

        if (!s.blocks().isEmpty()) {
            Material found = findForbidden(world, core, bounds.expand(s.blockRadius()), s.blocks());
            if (found != null) return new Result(Denial.FORBIDDEN_BLOCK, "<translate:" + found.translationKey() + ">");
        }
        return null;
    }

    /**
     * Ищет запрещённый блок в территории (плюс отступ). Высота ограничена ±64 от ядра,
     * чтобы приват «от бедрока до неба» не перебирал весь столб; проверяются только
     * загруженные чанки, которыми владеет текущий поток (Folia).
     */
    private Material findForbidden(World world, Location core, RegionBounds area, Set<Material> blocks) {
        int minY = Math.max(area.minY(), Math.max(world.getMinHeight(), core.getBlockY() - 64));
        int maxY = Math.min(area.maxY(), Math.min(world.getMaxHeight() - 1, core.getBlockY() + 64));
        for (int cx = area.minX() >> 4; cx <= area.maxX() >> 4; cx++) {
            for (int cz = area.minZ() >> 4; cz <= area.maxZ() >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                if (!plugin.getSchedulers().ownsLocation(new Location(world, (cx << 4) + 8, core.getY(), (cz << 4) + 8))) continue;
                ChunkSnapshot snapshot = world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false);
                int fromX = Math.max(area.minX(), cx << 4), toX = Math.min(area.maxX(), (cx << 4) + 15);
                int fromZ = Math.max(area.minZ(), cz << 4), toZ = Math.min(area.maxZ(), (cz << 4) + 15);
                for (int x = fromX; x <= toX; x++) {
                    for (int z = fromZ; z <= toZ; z++) {
                        for (int y = minY; y <= maxY; y++) {
                            Material type = snapshot.getBlockType(x & 15, y, z & 15);
                            if (blocks.contains(type)) return type;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Пауза считается с момента, когда владелец сам снёс свой приват (ломанием ядра или /ps delete). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeleted(RegionDeletedEvent event) {
        if (event.getPlayer() == null) return;
        if (event.getReason() != RegionDeleteEvent.Reason.BROKEN && event.getReason() != RegionDeleteEvent.Reason.COMMAND) return;
        if (!event.getRegion().isOwner(event.getPlayer().getUniqueId())) return;
        lastRemoval.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        if (lastRemoval.size() > 10_000) {
            long cutoff = System.currentTimeMillis() - settings().cooldownMs();
            lastRemoval.values().removeIf(at -> at < cutoff);
        }
    }
}
