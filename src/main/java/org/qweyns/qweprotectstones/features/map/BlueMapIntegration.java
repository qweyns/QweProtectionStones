package org.qweyns.qweprotectstones.features.map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Отрисовка границ приватов на BlueMap.
 *
 * <p>API BlueMap вызывается рефлексией — по тому же принципу, что и интеграция
 * с Dynmap: необязательная зависимость не должна требовать jar при сборке.
 * Если BlueMap нет или его API изменился, интеграция тихо выключается.</p>
 *
 * <p>Совместимо с BlueMapAPI v2.x: {@code BlueMapAPI.getWorld} →
 * {@code BlueMapWorld.getMaps} → {@code BlueMapMap.getMarkerSets} →
 * {@code MarkerSet.getMarkers} + {@code ShapeMarker}.</p>
 */
public class BlueMapIntegration {

    private final QweProtectStones plugin;

    private boolean active;

    private Object api;
    private Constructor<?> markerSetCtor;
    private Constructor<?> shapeMarkerCtor;
    private Constructor<?> colorCtor;
    private Method createRect;
    private Method apiGetWorld;
    private Method apiGetMaps;
    private Method worldGetMaps;
    private Method mapGetMarkerSets;
    private Method markerSetGetMarkers;
    private Method markerSetDetail;
    private Method markerSetLineColor;
    private Method markerSetFillColor;
    private Method markerSetLineWidth;

    public BlueMapIntegration(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return active;
    }

    /** Подключение: вызывается при старте плагина и при /qps reload. Идемпотентно. */
    public void enable() {
        if (!plugin.getConfigManager().getConfig().getBoolean("map.bluemap.enable", true)) {
            disable();
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("BlueMap") == null) return;

        try {
            Class<?> apiClass = Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");

            if (!prepareMethods()) return;

            // BlueMap может загрузиться позже нас — подписываемся на оба варианта.
            Method getInstance = apiClass.getMethod("getInstance");
            @SuppressWarnings("unchecked")
            Optional<Object> existing = (Optional<Object>) getInstance.invoke(null);
            if (existing.isPresent()) {
                attach(existing.get());
                return;
            }

            Method onEnable = apiClass.getMethod("onEnable", Consumer.class);
            Method onDisable = apiClass.getMethod("onDisable", Runnable.class);
            onEnable.invoke(null, (Consumer<Object>) this::attach);
            onDisable.invoke(null, (Runnable) this::detach);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "BlueMap найден, но API недоступно — интеграция отключена.", t);
        }
    }

    public void disable() {
        detach();
    }

    /** Ищем рефлексивные ссылки на нужные методы и конструкторы. */
    private boolean prepareMethods() {
        try {
            Class<?> markerSetClass = Class.forName("de.bluecolored.bluemap.api.markers.MarkerSet");
            Class<?> shapeMarkerClass = Class.forName("de.bluecolored.bluemap.api.markers.ShapeMarker");
            Class<?> shapeClass = Class.forName("de.bluecolored.bluemap.api.math.Shape");
            Class<?> colorClass = Class.forName("de.bluecolored.bluemap.api.math.Color");
            Class<?> apiClass = Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
            Class<?> worldClass = Class.forName("de.bluecolored.bluemap.api.BlueMapWorld");
            Class<?> mapClass = Class.forName("de.bluecolored.bluemap.api.BlueMapMap");

            markerSetCtor = markerSetClass.getConstructor(String.class, boolean.class, boolean.class);
            shapeMarkerCtor = shapeMarkerClass.getConstructor(String.class, shapeClass, float.class);
            colorCtor = colorClass.getConstructor(int.class, int.class, int.class, float.class);
            createRect = shapeClass.getMethod("createRect", double.class, double.class, double.class, double.class);

            apiGetWorld = apiClass.getMethod("getWorld", Object.class);
            apiGetMaps = apiClass.getMethod("getMaps");
            worldGetMaps = worldClass.getMethod("getMaps");
            mapGetMarkerSets = mapClass.getMethod("getMarkerSets");
            markerSetGetMarkers = markerSetClass.getMethod("getMarkers");
            markerSetDetail = shapeMarkerClass.getMethod("setDetail", String.class);
            markerSetLineColor = shapeMarkerClass.getMethod("setLineColor", colorClass);
            markerSetFillColor = shapeMarkerClass.getMethod("setFillColor", colorClass);
            markerSetLineWidth = shapeMarkerClass.getMethod("setLineWidth", int.class);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Неподдерживаемая версия API BlueMap — интеграция отключена.", t);
            return false;
        }
    }

    private void attach(Object bluemapApi) {
        this.api = bluemapApi;
        this.active = true;
        updateAll();
    }

    private void detach() {
        this.active = false;
        this.api = null;
    }

    /** Полная перерисовка: /qps reload и подключение BlueMap. */
    public void updateAll() {
        if (!active) return;
        for (Region region : plugin.getRegionManager().getAllRegions()) update(region);
    }

    /** Перерисовать один приват на всех картах его мира. */
    public void update(Region region) {
        if (!active || api == null || region == null) return;
        try {
            World world = Bukkit.getWorld(region.getWorldName());
            if (world == null) return;

            Optional<?> bmWorld = (Optional<?>) apiGetWorld.invoke(api, world);
            if (bmWorld.isEmpty()) return;

            for (Object map : (Collection<?>) worldGetMaps.invoke(bmWorld.get())) {
                draw(map, region);
            }
        } catch (Throwable t) {
            // Одна ошибка не должна ломать сервер: глушим интеграцию и пишем в лог.
            active = false;
            plugin.getLogger().log(Level.WARNING, "BlueMap: не удалось обновить маркер привата " + region.getShortId(), t);
        }
    }

    /** Убрать маркер привата со всех карт (мир мог уже выгрузиться — ищем везде). */
    public void remove(Region region) {
        if (!active || api == null || region == null) return;
        try {
            String markerId = markerId(region);
            for (Object map : (Collection<?>) apiGetMaps.invoke(api)) {
                Map<String, Object> markerSets = markerSetsOf(map);
                if (markerSets == null) continue;
                Object set = markerSets.get(setId());
                if (set == null) continue;
                ((Map<?, ?>) markerSetGetMarkers.invoke(set)).remove(markerId);
            }
        } catch (Throwable t) {
            active = false;
            plugin.getLogger().log(Level.WARNING, "BlueMap: не удалось удалить маркер привата " + region.getShortId(), t);
        }
    }

    private void draw(Object map, Region region) throws Exception {
        Map<String, Object> markerSets = markerSetsOf(map);
        if (markerSets == null) return;

        Object set = markerSets.get(setId());
        if (set == null) {
            set = markerSetCtor.newInstance(
                    plugin.getConfigManager().getConfig().getString("map.bluemap.marker_set_label", "Приваты"),
                    plugin.getConfigManager().getConfig().getBoolean("map.bluemap.toggleable", true),
                    plugin.getConfigManager().getConfig().getBoolean("map.bluemap.default_hidden", false));
            markerSets.put(setId(), set);
        }

        Map<String, Object> markers = markerSetsOf(set);
        if (markers == null) return;

        RegionBounds bounds = region.getBounds();
        String label = label(region);
        String detail = detail(region);

        // Границы блоков: прямоугольник от угла до угла, высота — верхняя грань.
        Object shape = createRect.invoke(null,
                (double) bounds.minX(), (double) bounds.minZ(),
                (double) bounds.maxX() + 1, (double) bounds.maxZ() + 1);

        Object marker = shapeMarkerCtor.newInstance(label, shape, (float) bounds.maxY() + 1);
        markerSetDetail.invoke(marker, detail);
        markerSetLineColor.invoke(marker, lineColor());
        markerSetFillColor.invoke(marker, fillColor());
        markerSetLineWidth.invoke(marker, lineWidth());

        markers.put(markerId(region), marker);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> markerSetsOf(Object owner) throws Exception {
        if (owner == null) return null;
        Object result = owner instanceof java.util.Map ? owner : mapGetMarkerSets.invoke(owner);
        // Map.getMarkerSets() возвращает Map<String, MarkerSet>, MarkerSet.getMarkers() — Map<String, Marker>.
        return result instanceof Map ? (Map<String, Object>) result : null;
    }

    private Object lineColor() throws Exception {
        int[] rgb = parseColor(plugin.getConfigManager().getConfig().getString("map.bluemap.line_color", "#00B4DB"));
        return colorCtor.newInstance(rgb[0], rgb[1], rgb[2], 1.0f);
    }

    private Object fillColor() throws Exception {
        int[] rgb = parseColor(plugin.getConfigManager().getConfig().getString("map.bluemap.fill_color", "#00B4DB"));
        float opacity = (float) Math.max(0.0, Math.min(1.0,
                plugin.getConfigManager().getConfig().getDouble("map.bluemap.fill_opacity", 0.25)));
        return colorCtor.newInstance(rgb[0], rgb[1], rgb[2], opacity);
    }

    private int lineWidth() {
        return Math.max(1, plugin.getConfigManager().getConfig().getInt("map.bluemap.line_width", 2));
    }

    private String setId() {
        return "qweprotectstones.regions";
    }

    private String markerId(Region region) {
        return "region-" + region.getShortId();
    }

    private String label(Region region) {
        String name = region.hasDisplayName() ? region.getDisplayName() : region.getOwnerName();
        return name + " [" + region.getShortId() + "]";
    }

    /** Всплывающая подсказка маркера (BlueMap рендерит этот HTML). */
    private String detail(Region region) {
        RegionBounds b = region.getBounds();
        return "<div style=\"font-family:sans-serif;line-height:1.4\">"
                + "<b>" + region.getOwnerName() + "</b><br>"
                + region.getTypeId() + " — " + b.sizeX() + "x" + b.sizeZ() + "<br>"
                + region.getWorldName() + " " + region.getCoreX() + "/" + region.getCoreY() + "/" + region.getCoreZ()
                + "</div>";
    }

    /** "#RRGGBB" → [r, g, b]; при неудаче — дефолтный голубой. */
    private int[] parseColor(String css) {
        if (css != null && css.matches("(?i)^#[0-9a-f]{6}$")) {
            return new int[]{
                    Integer.parseInt(css.substring(1, 3), 16),
                    Integer.parseInt(css.substring(3, 5), 16),
                    Integer.parseInt(css.substring(5, 7), 16)};
        }
        return new int[]{0x00, 0xB4, 0xDB};
    }
}
