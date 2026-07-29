package org.qweyns.qweprotectstones.features.map;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Отрисовка границ приватов на Dynmap.
 *
 * <p>API Dynmap вызывается рефлексией: подключать его как зависимость ради
 * необязательной интеграции — значит требовать jar при сборке. Если Dynmap нет
 * или его API изменилось, интеграция просто выключается.</p>
 */
public class DynmapIntegration {

    private final QweProtectStones plugin;

    private Object markerSet;
    private Method createAreaMarker;
    private Method deleteMarker;
    private Method findAreaMarker;
    private Method setLineStyle;
    private Method setFillStyle;
    private Method setDescription;

    private boolean active;

    public DynmapIntegration(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return active;
    }

    public void enable() {
        if (!plugin.getConfigManager().getConfig().getBoolean("map.dynmap.enable", true)) return;

        Plugin dynmap = Bukkit.getPluginManager().getPlugin("dynmap");
        if (dynmap == null || !dynmap.isEnabled()) return;

        try {
            Object markerApi = dynmap.getClass().getMethod("getMarkerAPI").invoke(dynmap);
            if (markerApi == null) {
                plugin.getLogger().warning("Dynmap найден, но MarkerAPI недоступен — интеграция отключена.");
                return;
            }

            String setId = "qweprotectstones.regions";
            String label = plugin.getConfigManager().getConfig().getString("map.dynmap.layer_name", "Приваты");

            Class<?> markerApiClass = markerApi.getClass();
            Object set = markerApiClass.getMethod("getMarkerSet", String.class).invoke(markerApi, setId);
            if (set == null) {
                set = markerApiClass.getMethod("createMarkerSet", String.class, String.class,
                        java.util.Set.class, boolean.class).invoke(markerApi, setId, label, null, false);
            }
            if (set == null) {
                plugin.getLogger().warning("Не удалось создать слой Dynmap — интеграция отключена.");
                return;
            }

            markerSet = set;
            Class<?> setClass = set.getClass();
            createAreaMarker = findMethod(setClass, "createAreaMarker", String.class, String.class, boolean.class,
                    String.class, double[].class, double[].class, boolean.class);
            findAreaMarker = findMethod(setClass, "findAreaMarker", String.class);

            Class<?> areaMarkerClass = Class.forName("org.dynmap.markers.AreaMarker");
            deleteMarker = areaMarkerClass.getMethod("deleteMarker");
            setLineStyle = areaMarkerClass.getMethod("setLineStyle", int.class, double.class, int.class);
            setFillStyle = areaMarkerClass.getMethod("setFillStyle", double.class, int.class);
            setDescription = areaMarkerClass.getMethod("setDescription", String.class);

            active = true;
            plugin.getLogger().info("Интеграция с Dynmap включена.");
            redrawAll();
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось подключиться к Dynmap — интеграция отключена.", e);
            active = false;
        }
    }

    private Method findMethod(Class<?> owner, String name, Class<?>... params) throws NoSuchMethodException {
        try {
            Method method = owner.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            for (Class<?> iface : owner.getInterfaces()) {
                try {
                    return findMethod(iface, name, params);
                } catch (NoSuchMethodException ignored) {
                    // пробуем следующий интерфейс
                }
            }
            throw e;
        }
    }

    /** Полная перерисовка — на запуске и после /qps reload. */
    public void redrawAll() {
        if (!active) return;

        for (Region region : plugin.getRegionManager().getAllRegions()) {
            update(region);
        }
    }

    public void update(Region region) {
        if (!active || region == null) return;

        try {
            RegionBounds bounds = region.getBounds();
            double[] x = {bounds.minX(), bounds.maxX() + 1.0};
            double[] z = {bounds.minZ(), bounds.maxZ() + 1.0};

            String id = markerId(region);
            Object marker = findAreaMarker.invoke(markerSet, id);
            if (marker == null) {
                marker = createAreaMarker.invoke(markerSet, id, label(region), false,
                        region.getWorldName(), x, z, false);
            }
            if (marker == null) return;

            int lineColor = color("map.dynmap.line_color", 0x00B4DB);
            int fillColor = color("map.dynmap.fill_color", 0x00B4DB);

            setLineStyle.invoke(marker, 2, 0.8, lineColor);
            setFillStyle.invoke(marker, plugin.getConfigManager().getConfig().getDouble("map.dynmap.fill_opacity", 0.25), fillColor);
            setDescription.invoke(marker, description(region));
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.FINE, "Не удалось обновить метку Dynmap", e);
        }
    }

    public void remove(Region region) {
        if (!active || region == null) return;

        try {
            Object marker = findAreaMarker.invoke(markerSet, markerId(region));
            if (marker != null) deleteMarker.invoke(marker);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.FINE, "Не удалось удалить метку Dynmap", e);
        }
    }

    private int color(String path, int fallback) {
        String raw = plugin.getConfigManager().getConfig().getString(path, "");
        if (raw == null || raw.isBlank()) return fallback;

        try {
            return Integer.parseInt(raw.startsWith("#") ? raw.substring(1) : raw, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String markerId(Region region) {
        return "qps_" + region.getId();
    }

    private String label(Region region) {
        return region.getLabel();
    }

    private String description(Region region) {
        return "<b>" + label(region) + "</b><br/>"
                + "Тип: " + region.getTypeId() + "<br/>"
                + "Прочность: " + region.getDurability() + "/" + region.getMaxDurability() + "<br/>"
                + "Участников: " + region.getMemberCount();
    }
}
