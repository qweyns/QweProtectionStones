package org.qweyns.qweprotectstones.features.visual;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.qweyns.qweprotectstones.scheduler.Schedulers;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.config.ParticleSetting;
import org.qweyns.qweprotectstones.config.SoundSetting;
import org.qweyns.qweprotectstones.utils.ColorUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class VisualManager {

    private final QweProtectStones plugin;
    private final Set<UUID> activeBoundaries = ConcurrentHashMap.newKeySet();
    private final Map<UUID, GlowData> glowingTasks = new ConcurrentHashMap<>();

    private record GlowData(UUID regionId, Schedulers.Task task) {
    }

    public VisualManager(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public void playEffect(Location loc, String typeId, String action) {
        World world = loc == null ? null : loc.getWorld();
        if (world == null) return;

        String path = plugin.getConfigManager().getEffectPath(typeId, action);
        if (path == null) return;

        FileConfiguration cfg = plugin.getRegionConfig().raw();
        String soundName = cfg.getString(path + ".sound");
        if (soundName != null && !soundName.isBlank()) {

            SoundSetting sound = SoundSetting.parse(soundName
                            + ":" + cfg.getDouble(path + ".volume", 1.0)
                            + ":" + cfg.getDouble(path + ".pitch", 1.0),
                    SoundSetting.NONE);
            if (!sound.isEnabled()) {
                plugin.getLogger().warning("Неизвестный звук в config.yml (" + path + ".sound): " + soundName);
            }
            sound.playAt(loc);
        }

        if (cfg.getBoolean(path + ".lightning", false)) world.strikeLightningEffect(loc);
    }

    public boolean toggleGlow(Player player, Region region) {
        UUID uuid = player.getUniqueId();

        GlowData existing = glowingTasks.remove(uuid);
        if (existing != null) {
            existing.task().cancel();
            return false;
        }

        List<Location> points = buildWireframe(region);
        if (points.isEmpty()) return false;

        boolean[] blinkState = {true};
        Schedulers.Task[] handle = new Schedulers.Task[1];
        handle[0] = plugin.getSchedulers().runTimer(() -> {
            if (!player.isOnline()) {
                if (handle[0] != null) handle[0].cancel();
                glowingTasks.remove(uuid);
                return;
            }

            Color color = blinkState[0] ? Color.LIME : Color.GREEN;
            blinkState[0] = !blinkState[0];

            plugin.getTunables().particles().spawnFor(player, points, color);
        }, 0L, plugin.getTunables().animationPeriodTicks());
        Schedulers.Task task = handle[0];

        glowingTasks.put(uuid, new GlowData(region.getId(), task));
        return true;
    }

    public void removeGlow(UUID regionId) {
        glowingTasks.values().removeIf(data -> {
            if (data.regionId().equals(regionId)) {
                data.task().cancel();
                return true;
            }
            return false;
        });
    }

    public void showBoundary(Region region, String animationType) {
        if (region == null || !activeBoundaries.add(region.getId())) return;

        List<Location> points = buildWireframe(region);
        if (points.isEmpty()) {
            activeBoundaries.remove(region.getId());
            return;
        }

        World world = region.getWorld();
        if (world == null) {
            activeBoundaries.remove(region.getId());
            return;
        }

        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        long durationTicks = cfg.getLong("visuals.boundaries.show_time_ticks", 40L);
        Color color = borderColorOf(region, animationType, cfg);
        UUID regionId = region.getId();

        final long period = plugin.getTunables().animationPeriodTicks();
        final long[] elapsed = {0};
        Schedulers.Task[] handle = new Schedulers.Task[1];
        handle[0] = plugin.getSchedulers().runTimer(() -> {
            if (elapsed[0] >= durationTicks) {
                activeBoundaries.remove(regionId);
                if (handle[0] != null) handle[0].cancel();
                return;
            }

            spawnBoundaryParticles(world, points, color);
            elapsed[0] += period;
        }, 0L, period);
    }

    // Folia: точки каркаса раскиданы по чужим регионам — частицу в каждом
    // чанке показывает поток-хозяин этого чанка
    private void spawnBoundaryParticles(World world, List<Location> points, Color color) {
        if (points.isEmpty()) return;

        Map<Long, List<Location>> byChunk = new HashMap<>();
        for (Location point : points) {
            long key = ((long) (point.getBlockX() >> 4) << 32) | (point.getBlockZ() >> 4 & 0xFFFFFFFFL);
            byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(point);
        }

        ParticleSetting particles = plugin.getTunables().particles();
        for (List<Location> group : byChunk.values()) {
            plugin.getSchedulers().runAtLocation(group.get(0), () -> particles.spawnIn(world, group, color));
        }
    }

    private Color borderColorOf(Region region, String animationType, FileConfiguration cfg) {
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        if (type != null && type.hasBorderColor()) return ColorUtil.parseParticleColor(type.borderColor());

        return colorFor(animationType, cfg);
    }

    private Color colorFor(String animationType, FileConfiguration cfg) {
        return switch (animationType) {
            case "create" -> ColorUtil.parseParticleColor(cfg.getString("visuals.boundaries.color_open", "#00FF00"));
            case "remove" -> ColorUtil.parseParticleColor(cfg.getString("visuals.boundaries.color_closed", "#FF0000"));
            case "info" -> ColorUtil.parseParticleColor(cfg.getString("visuals.boundaries.color_info", "#00B4DB"));
            default -> Color.WHITE;
        };
    }

    private List<Location> buildWireframe(Region region) {
        return wireframeOf(region.getWorld(), region.getBounds(), region.getCoreY());
    }

    private List<Location> wireframeOf(World world, RegionBounds bounds, int aroundY) {
        List<Location> points = new ArrayList<>();
        if (world == null) return points;

        ParticleSetting particles = plugin.getTunables().particles();
        int maxPoints = particles.maxPoints();

        double minX = bounds.minX();
        double minZ = bounds.minZ();
        double maxX = bounds.maxX() + 1.0;
        double maxZ = bounds.maxZ() + 1.0;

        // по вертикали рисуем участок вокруг ядра, не всю высоту

        double minY = Math.max(bounds.minY(), aroundY - 8);
        double maxY = Math.min(bounds.maxY() + 1.0, aroundY + 9);

        double stepX = particles.step(maxX - minX);
        double stepY = particles.step(maxY - minY);
        double stepZ = particles.step(maxZ - minZ);

        for (double x = minX; x <= maxX && points.size() < maxPoints; x += stepX) {
            points.add(new Location(world, x, minY, minZ));
            points.add(new Location(world, x, maxY, minZ));
            points.add(new Location(world, x, minY, maxZ));
            points.add(new Location(world, x, maxY, maxZ));
        }
        for (double y = minY; y <= maxY && points.size() < maxPoints; y += stepY) {
            points.add(new Location(world, minX, y, minZ));
            points.add(new Location(world, maxX, y, minZ));
            points.add(new Location(world, minX, y, maxZ));
            points.add(new Location(world, maxX, y, maxZ));
        }
        for (double z = minZ; z <= maxZ && points.size() < maxPoints; z += stepZ) {
            points.add(new Location(world, minX, minY, z));
            points.add(new Location(world, maxX, minY, z));
            points.add(new Location(world, minX, maxY, z));
            points.add(new Location(world, maxX, maxY, z));
        }
        return points;
    }

    public void spawnDamageIndicator(Location loc, int damage) {
        if (!plugin.getConfigManager().isDamageIndicatorEnabled()) return;

        World world = loc.getWorld();
        if (world == null) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location spawnLoc = loc.clone().add(
                0.5 + random.nextDouble(-0.2, 0.2),
                1.2,
                0.5 + random.nextDouble(-0.2, 0.2));

        TextDisplay display = world.spawn(spawnLoc, TextDisplay.class, td -> {
            td.text(plugin.getConfigManager().getDamageIndicator(damage));
            td.setBillboard(Display.Billboard.CENTER);
            td.setDefaultBackground(false);
            td.setTeleportDuration(30);
        });

        display.teleport(spawnLoc.clone().add(0, 1.2, 0));
        plugin.getSchedulers().runLater(() -> {
            if (display.isValid()) display.remove();
        }, 35L);
    }

    public void shutdown() {
        glowingTasks.values().forEach(data -> data.task().cancel());
        glowingTasks.clear();
        activeBoundaries.clear();
    }
}
