package org.qweyns.qweprotectstones.holograms;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.config.ConfigManager;
import org.qweyns.qweprotectstones.utils.ColorUtil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DHProvider implements IHologramProvider {

    private final QweProtectStones plugin;
    // мутируется из потоков разных регионов на Folia
    private final Set<String> activeHolograms = ConcurrentHashMap.newKeySet();

    private final Map<String, Method> optionalSetters = new ConcurrentHashMap<>();
    private final Set<String> missingSetters = ConcurrentHashMap.newKeySet();

    public DHProvider(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    @Override
    public void createOrUpdate(Region region, Location coreLocation) {
        ConfigManager config = plugin.getConfigManager();

        String name = hologramName(region.getId());
        String typeId = region.getTypeId();

        Location hologramLoc = coreLocation.clone().add(0.5, config.getHologramOffset(typeId), 0.5);

        // DHAPI создаёт и двигает сущности в мире — на Folia это можно только из потока региона
        plugin.getSchedulers().runAtLocation(hologramLoc, () -> {
            Hologram holo = DHAPI.getHologram(name);
            if (holo == null) {
                holo = DHAPI.createHologram(name, hologramLoc);
            }

            holo.setSaveToFile(false);

            DHAPI.setHologramLines(holo, buildLines(region, typeId, config));
            holo.setDisplayRange(config.getHologramRange(typeId));

            applyOptionalSettings(holo, typeId, config);

            activeHolograms.add(name);
        });
    }

    private List<String> buildLines(Region region, String typeId, ConfigManager config) {
        // заголовок задаётся строкой с %type%, display_name не подставляется

        List<String> lines = new ArrayList<>();
        for (String line : config.getHologramLines(typeId, plugin.isUnderSiege(region))) {
            lines.add(ColorUtil.formatLegacyString(HologramText.apply(plugin, line, region)));
        }
        return lines;
    }

    private void applyOptionalSettings(Hologram holo, String typeId, ConfigManager config) {
        set(holo, "setUpdateRange", int.class, config.getHologramUpdateRange(typeId));
        set(holo, "setUpdateInterval", int.class, config.getHologramUpdateInterval(typeId));
        set(holo, "setDownOrigin", boolean.class, config.isHologramDownOrigin(typeId));

        String permission = config.getHologramPermission(typeId);
        if (!permission.isBlank()) set(holo, "setPermission", String.class, permission);
    }

    private void set(Hologram holo, String setter, Class<?> paramType, Object value) {
        if (missingSetters.contains(setter)) return;

        Method method = optionalSetters.computeIfAbsent(setter, key -> {
            try {
                return holo.getClass().getMethod(key, paramType);
            } catch (NoSuchMethodException e) {
                return null;
            }
        });

        if (method == null) {
            optionalSetters.remove(setter);
            if (missingSetters.add(setter)) {
                plugin.getLogger().warning("DecentHolograms не поддерживает " + setter
                        + " — эта настройка голограмм пропущена.");
            }
            return;
        }

        try {
            method.invoke(holo, value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (missingSetters.add(setter)) {
                plugin.getLogger().warning("Не удалось применить " + setter
                        + " к голограмме DecentHolograms: " + e.getMessage());
            }
        }
    }

    @Override
    public void remove(UUID regionId) {
        String name = hologramName(regionId);

        Hologram holo = DHAPI.getHologram(name);
        if (holo != null) holo.delete();
        activeHolograms.remove(name);
    }

    @Override
    public void deleteAll() {
        for (String name : activeHolograms) {
            Hologram holo = DHAPI.getHologram(name);
            if (holo != null) holo.delete();
        }
        activeHolograms.clear();
    }

    private static String hologramName(UUID regionId) {
        return "region_" + regionId;
    }
}
