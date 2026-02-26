package org.qweyns.pshologramm.features.visual;

import dev.espi.protectionstones.PSRegion;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.utils.ColorUtil;

import java.util.*;

public class VisualManager {
    private final PSHologramm plugin;
    private final Set<String> activeVisuals = new HashSet<>();

    private static class GlowData {
        String regionId;
        BukkitTask task;
        GlowData(String r, BukkitTask t) { this.regionId = r; this.task = t; }
    }
    private final Map<UUID, GlowData> glowingTasks = new HashMap<>();

    public VisualManager(PSHologramm plugin) { this.plugin = plugin; }

    public void playEffect(Location loc, String type, String action) {
        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        String path = "regions." + type + "." + action;
        if (!cfg.contains(path)) return;

        String sName = cfg.getString(path + ".sound");
        if (sName != null && !sName.isEmpty()) {
            try {
                loc.getWorld().playSound(loc, Sound.valueOf(sName),
                        (float) cfg.getDouble(path + ".volume", 1.0),
                        (float) cfg.getDouble(path + ".pitch", 1.0));
            } catch (Exception ignored) {}
        }
        if (cfg.getBoolean(path + ".lightning", false)) loc.getWorld().strikeLightningEffect(loc);
    }

    public boolean toggleGlow(Player player, PSRegion region) {
        UUID uuid = player.getUniqueId();
        if (glowingTasks.containsKey(uuid)) {
            glowingTasks.get(uuid).task.cancel();
            glowingTasks.remove(uuid);
            return false;
        }

        List<Location> preCalculatedPoints = buildWireframePoints(region);

        BukkitTask task = new BukkitRunnable() {
            boolean blinkState = true;
            @Override
            public void run() {
                if (!player.isOnline()) { this.cancel(); glowingTasks.remove(uuid); return; }
                Color color = blinkState ? Color.LIME : Color.GREEN;
                blinkState = !blinkState;
                Particle.DustOptions options = new Particle.DustOptions(color, 1.5f);
                for (Location pt : preCalculatedPoints) player.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, options);
            }
        }.runTaskTimer(plugin, 0L, 10L);

        glowingTasks.put(uuid, new GlowData(region.getId(), task));
        return true;
    }

    public void removeGlow(String regionId) {
        glowingTasks.values().removeIf(data -> {
            if (data.regionId.equals(regionId)) { data.task.cancel(); return true; }
            return false;
        });
    }

    public void displayRegionBoundaries(PSRegion region, boolean open) {
        String regionId = region.getId();
        if (!activeVisuals.add(regionId)) return;

        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        Color color = ColorUtil.parseParticleColor(open ? cfg.getString("visuals.boundaries.color_open", "#00FF00") : cfg.getString("visuals.boundaries.color_closed", "#FF0000"));
        long ticks = cfg.getLong("visuals.boundaries.show_time_ticks", 40L);

        startWireframeTask(regionId, region, new Particle.DustOptions(color, 1.0f), ticks, 20L);
    }

    public void playRedstoneBoundary(PSRegion region, String animationType) {
        String regionId = region.getId();
        if (!activeVisuals.add(regionId)) return;

        long ticks = animationType.equals("glow") ? 100L : 40L;
        List<Location> points = buildWireframePoints(region);

        new BukkitRunnable() {
            int elapsed = 0;
            boolean blinkState = true;
            @Override
            public void run() {
                if (elapsed >= ticks) { activeVisuals.remove(regionId); this.cancel(); return; }

                Color color = switch (animationType) {
                    case "create" -> Color.LIME;
                    case "remove" -> Color.RED;
                    case "glow" -> { blinkState = !blinkState; yield blinkState ? Color.LIME : Color.GREEN; }
                    default -> Color.WHITE;
                };

                Particle.DustOptions options = new Particle.DustOptions(color, 1.5f);
                for (Location pt : points) region.getWorld().spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, options);
                elapsed += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void startWireframeTask(String regionId, PSRegion region, Particle.DustOptions options, long totalTicks, long period) {
        List<Location> points = buildWireframePoints(region);
        new BukkitRunnable() {
            int elapsed = 0;
            @Override
            public void run() {
                if (elapsed >= totalTicks) { activeVisuals.remove(regionId); this.cancel(); return; }
                for (Location pt : points) region.getWorld().spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, options);
                elapsed += period;
            }
        }.runTaskTimer(plugin, 0L, period);
    }

    private List<Location> buildWireframePoints(PSRegion region) {
        List<Location> points = new ArrayList<>();
        Location min = new Location(region.getWorld(), region.getWGRegion().getMinimumPoint().x(), region.getWGRegion().getMinimumPoint().y(), region.getWGRegion().getMinimumPoint().z());
        Location max = new Location(region.getWorld(), region.getWGRegion().getMaximumPoint().x() + 1.0, region.getWGRegion().getMaximumPoint().y() + 1.0, region.getWGRegion().getMaximumPoint().z() + 1.0);

        double stepX = (max.getX() - min.getX()) > 32 ? 2.0 : 1.0;
        double stepY = (max.getY() - min.getY()) > 32 ? 2.0 : 1.0;
        double stepZ = (max.getZ() - min.getZ()) > 32 ? 2.0 : 1.0;

        for (double x = min.getX(); x <= max.getX(); x += stepX) {
            points.add(new Location(min.getWorld(), x, min.getY(), min.getZ())); points.add(new Location(min.getWorld(), x, max.getY(), min.getZ()));
            points.add(new Location(min.getWorld(), x, min.getY(), max.getZ())); points.add(new Location(min.getWorld(), x, max.getY(), max.getZ()));
        }
        for (double y = min.getY(); y <= max.getY(); y += stepY) {
            points.add(new Location(min.getWorld(), min.getX(), y, min.getZ())); points.add(new Location(min.getWorld(), max.getX(), y, min.getZ()));
            points.add(new Location(min.getWorld(), min.getX(), y, max.getZ())); points.add(new Location(min.getWorld(), max.getX(), y, max.getZ()));
        }
        for (double z = min.getZ(); z <= max.getZ(); z += stepZ) {
            points.add(new Location(min.getWorld(), min.getX(), min.getY(), z)); points.add(new Location(min.getWorld(), max.getX(), min.getY(), z));
            points.add(new Location(min.getWorld(), min.getX(), max.getY(), z)); points.add(new Location(min.getWorld(), max.getX(), max.getY(), z));
        }
        return points;
    }

    public void spawnDamageIndicator(Location loc) {
        if (!plugin.getConfigManager().isDamageIndicatorEnabled()) return;

        Location spawnLoc = loc.clone().add(0.5, 1.2, 0.5);
        spawnLoc.add((Math.random() - 0.5) * 0.4, 0, (Math.random() - 0.5) * 0.4);

        TextDisplay display = loc.getWorld().spawn(spawnLoc, TextDisplay.class, td -> {
            td.text(plugin.getConfigManager().getDamageIndicator());
            td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            td.setDefaultBackground(false);
            td.setTeleportDuration(30);
        });

        display.teleport(spawnLoc.clone().add(0, 1.2, 0));
        Bukkit.getScheduler().runTaskLater(plugin, display::remove, 35L);
    }
}
