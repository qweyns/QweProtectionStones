package org.qweyns.pshologramm.holograms;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.joml.Vector3f;
import org.qweyns.pshologramm.PSHologramm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class FHProvider implements IHologramProvider {
    private final PSHologramm plugin;
    private final Set<String> activeHolograms = new HashSet<>();

    public FHProvider(PSHologramm plugin) {
        this.plugin = plugin;
    }

    @Override
    public void createOrUpdate(String regionId, Location loc, String regionType, String owner, int dur, int maxDur) {
        String name = "region_" + regionId;
        de.oliver.fancyholograms.api.HologramManager fhManager = FancyHologramsPlugin.get().getHologramManager();

        Hologram holo = fhManager.getHologram(name).orElse(null);

        if (holo == null) {
            double offset = plugin.getConfigManager().getHologramOffset(regionType);
            TextHologramData data = new TextHologramData(name, loc.clone().add(0.5, offset, 0.5));
            holo = fhManager.create(data);
            fhManager.addHologram(holo);
        }

        if (holo.getData() instanceof TextHologramData textData) {
            List<String> rawLines = plugin.getConfigManager().getRegionLines(regionType);
            List<String> finalLines = new ArrayList<>();
            finalLines.add(plugin.getConfigManager().getRegionName(regionType));

            for (String line : rawLines) {
                line = line.replace("%player%", owner)
                        .replace("%durability%", String.valueOf(dur))
                        .replace("%max_durability%", String.valueOf(maxDur))
                        .replace("%item%", plugin.getConfigManager().getUpgradeItem().name());
                finalLines.add(line);
            }

            textData.setText(finalLines);
            textData.setTextShadow(plugin.getConfigManager().hasShadow(regionType));
            float scale = plugin.getConfigManager().getScale(regionType);
            textData.setScale(new Vector3f(scale, scale, scale));

            try {
                textData.setBillboard(Display.Billboard.valueOf(plugin.getConfigManager().getBillboard(regionType).toUpperCase()));
            } catch (Exception e) {
                textData.setBillboard(Display.Billboard.CENTER);
            }

            holo.forceUpdate();
        }
        activeHolograms.add(name);
        cleanupMemory();
    }

    @Override
    public void remove(String regionId) {
        String name = "region_" + regionId;
        de.oliver.fancyholograms.api.HologramManager fhManager = FancyHologramsPlugin.get().getHologramManager();

        fhManager.getHologram(name).ifPresent(fhManager::removeHologram);
        activeHolograms.remove(name);
    }

    @Override
    public void deleteAll() {
        de.oliver.fancyholograms.api.HologramManager fhManager = FancyHologramsPlugin.get().getHologramManager();
        for (String name : activeHolograms) {
            fhManager.getHologram(name).ifPresent(fhManager::removeHologram);
        }
        activeHolograms.clear();
    }

    private void cleanupMemory() {
        de.oliver.fancyholograms.api.HologramManager fhManager = FancyHologramsPlugin.get().getHologramManager();
        Iterator<String> it = activeHolograms.iterator();
        while (it.hasNext()) {
            String name = it.next();
            if (fhManager.getHologram(name).isEmpty()) {
                it.remove();
            }
        }
    }
}
