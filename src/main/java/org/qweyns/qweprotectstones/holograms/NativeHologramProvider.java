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
    private final Map<UUID, TextDisplay> active = new ConcurrentHashMap<>();
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
        plugin.getSchedulers().runAtLocation(loc, () -> spawnOrUpdate(region, typeId, world, loc, config));
    }

    private void spawnOrUpdate(Region region, String typeId, World world, Location loc, ConfigManager config) {
        TextDisplay display = active.get(region.getId());
        // после выгрузки чанка сущность недействительна, а перенос ядра проще
        // пережить пересозданием, чем телепортом из чужого потока
        if (display == null || !display.isValid() || !display.getWorld().equals(world)
                || display.getLocation().distanceSquared(loc) > 0.01) {
            remove(region.getId());
            display = world.spawn(loc, TextDisplay.class, spawned -> configure(spawned, typeId, config));
            active.put(region.getId(), display);
        }

        display.text(buildText(region, typeId));
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
        TextDisplay display = active.remove(regionId);
        if (display == null || !display.isValid()) return;

        plugin.getSchedulers().runAtEntity(display, display::remove);
    }

    @Override
    public void deleteAll() {
        for (TextDisplay display : active.values()) {
            if (display.isValid()) display.remove();
        }
        active.clear();
    }
}
