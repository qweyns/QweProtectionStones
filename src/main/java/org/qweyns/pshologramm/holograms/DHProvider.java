package org.qweyns.pshologramm.holograms;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.utils.ColorUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class DHProvider implements IHologramProvider {
    private final PSHologramm plugin;
    private final Set<String> activeHolograms = new HashSet<>();

    public DHProvider(PSHologramm plugin) {
        this.plugin = plugin;
    }

    @Override
    public void createOrUpdate(String regionId, Location loc, String regionType, String owner, int dur, int maxDur) {
        String name = "region_" + regionId;
        Hologram holo = DHAPI.getHologram(name);

        if (holo == null) {
            double offset = plugin.getConfigManager().getHologramOffset(regionType);
            holo = DHAPI.createHologram(name, loc.clone().add(0.5, offset, 0.5));
            holo.setSaveToFile(false);
        }

        List<String> rawLines = plugin.getConfigManager().getRegionLines(regionType);
        List<String> finalLines = new ArrayList<>();
        finalLines.add(ColorUtil.formatLegacyString(plugin.getConfigManager().getRegionName(regionType)));

        for (String line : rawLines) {
            line = line.replace("%player%", owner)
                    .replace("%durability%", String.valueOf(dur))
                    .replace("%max_durability%", String.valueOf(maxDur))
                    .replace("%item%", plugin.getConfigManager().getUpgradeItem().name());
            finalLines.add(ColorUtil.formatLegacyString(line));
        }

        DHAPI.setHologramLines(holo, finalLines);
        holo.setDisplayRange(plugin.getConfigManager().getHologramRange(regionType));
        holo.setSaveToFile(false);

        activeHolograms.add(name);
        cleanupMemory();
    }

    @Override
    public void remove(String regionId) {
        String name = "region_" + regionId;
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

    private void cleanupMemory() {
        Iterator<String> it = activeHolograms.iterator();
        while (it.hasNext()) {
            String name = it.next();
            if (DHAPI.getHologram(name) == null) {
                it.remove();
            }
        }
    }
}
