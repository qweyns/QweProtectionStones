package org.qweyns.qweprotectstones.holograms;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.config.ConfigManager;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.utils.ColorUtil;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Встроенные голограммы на TextDisplay — работают без сторонних плагинов.
 * Настройки те же, что у мостов DecentHolograms/FancyHolograms
 * (regions.yml: hologram_settings). Не поддерживается только permission.
 */
public class NativeHologramProvider implements IHologramProvider {

    private final QweProtectStones plugin;
    private record Entry(TextDisplay display, Location location) { }
    private final Map<UUID, Entry> active = new ConcurrentHashMap<>();
    private final Map<UUID, Object> requests = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public NativeHologramProvider(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    @Override
    public void createOrUpdate(Region region, Location coreLocation) {
        World world = coreLocation.getWorld();
        if (world == null) return;

        ConfigManager config = plugin.getConfigManager();
        String typeId = region.getTypeId();
        Location loc = coreLocation.clone().add(0.5, config.getHologramOffset(typeId), 0.5);

        // спавн и правки сущности — из потока-хозяина региона (Folia)
        Object request = new Object();
        requests.put(region.getId(), request);
        plugin.getSchedulers().runAtLocation(loc, () -> spawnOrUpdate(region, typeId, world, loc, config, request));
    }

    private void spawnOrUpdate(Region region, String typeId, World world, Location loc, ConfigManager config, Object request) {
        if (!plugin.isEnabled() || requests.get(region.getId()) != request
                || plugin.getRegionManager().getById(region.getId()) != region) return;
        Location current = region.getCoreLocation();
        if (current == null || !current.clone().add(0.5, config.getHologramOffset(typeId), 0.5).equals(loc)) return;
        Entry previous = active.get(region.getId());
        if (previous != null && previous.location().equals(loc)) {
            plugin.getSchedulers().runAtEntity(previous.display(), () -> {
                if (requests.get(region.getId()) != request) return;
                if (!previous.display().isValid() || !previous.display().getLocation().equals(loc)) {
                    active.remove(region.getId(),previous);
                    previous.display().remove();
                    return;
                }
                previous.display().text(buildText(region, typeId));
            }, () -> active.remove(region.getId(), previous));
            return;
        }
        TextDisplay display = world.spawn(loc, TextDisplay.class, spawned -> configure(spawned, typeId, config));
        display.text(buildText(region, typeId));
        Entry replacement = new Entry(display, loc.clone());
        java.util.concurrent.atomic.AtomicBoolean accepted = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<Entry> old = new java.util.concurrent.atomic.AtomicReference<>();
        requests.computeIfPresent(region.getId(), (id, latest) -> {
            if (latest == request && plugin.getRegionManager().getById(id) == region) {
                old.set(active.put(id, replacement));
                accepted.set(true);
            }
            return latest;
        });
        if (!accepted.get()) display.remove();
        if (old.get() != null) removeDisplay(old.get().display());
    }

    private void removeDisplay(TextDisplay display) {
        if (plugin.getSchedulers().ownsEntity(display)) display.remove();
        else if (plugin.isEnabled()) plugin.getSchedulers().runAtEntity(display, display::remove);
        // При остановке Folia чужие entity-потоки уже недоступны. Display непостоянный:
        // его не сохраняет мир; прямое обращение из shutdown-потока запрещено.
    }

    private Component buildText(Region region, String typeId) {
        StringBuilder text = new StringBuilder();
        for (String line : plugin.getConfigManager().getHologramLines(typeId, plugin.isUnderSiege(region))) {
            if (!text.isEmpty()) text.append('\n');
            text.append(HologramText.apply(plugin, line, region));
        }
        return ColorUtil.formatComponent(text.toString());
    }

    private void configure(TextDisplay display, String typeId, ConfigManager config) {
        display.setPersistent(false);
        display.setBillboard(billboard(config.getBillboard(typeId)));
        display.setAlignment(alignment(config.getHologramAlignment(typeId)));
        display.setShadowed(config.hasShadow(typeId));
        display.setSeeThrough(config.isHologramSeeThrough(typeId));
        display.setViewRange(config.getHologramRange(typeId));

        float scale = config.getScale(typeId);
        display.setTransformation(new Transformation(
                new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));

        String background = config.getHologramBackground(typeId);
        if (!background.isBlank()) {
            Color color = parseColor(background);
            if (color != null) display.setBackgroundColor(color);
        }

        if (!config.getHologramPermission(typeId).isBlank()) {
            warnOnce("permission", "Право на просмотр для NATIVE-голограмм не поддерживается — настройка пропущена.");
        }
    }

    private Display.Billboard billboard(String raw) {
        try {
            return Display.Billboard.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warnOnce("billboard:" + raw, "Неизвестный billboard '" + raw + "' — использую CENTER.");
            return Display.Billboard.CENTER;
        }
    }

    private TextDisplay.TextAlignment alignment(String raw) {
        try {
            return TextDisplay.TextAlignment.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warnOnce("alignment:" + raw, "Неизвестное выравнивание '" + raw + "' — использую CENTER.");
            return TextDisplay.TextAlignment.CENTER;
        }
    }

    private Color parseColor(String raw) {
        try {
            return Color.fromARGB((int) Long.parseLong(raw.replace("#", ""), 16));
        } catch (NumberFormatException e) {
            warnOnce("color:" + raw, "Неизвестный цвет подложки '" + raw + "' — нужен формат #AARRGGBB.");
            return null;
        }
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key)) plugin.getLogger().warning(message);
    }

    @Override
    public void remove(UUID regionId) {
        requests.remove(regionId);
        Entry entry = active.remove(regionId);
        if (entry != null) removeDisplay(entry.display());
    }

    @Override
    public void deleteAll() {
        requests.clear();
        for (Entry entry : active.values()) removeDisplay(entry.display());
        active.clear();
    }
}
