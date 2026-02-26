package org.qweyns.pshologramm.features.penalty;

import org.bukkit.configuration.file.FileConfiguration;
import org.qweyns.pshologramm.PSHologramm;

import java.util.HashMap;
import java.util.Map;

public class PenaltyManager {
    private final PSHologramm plugin;
    private final Map<String, Long> lastAttacked = new HashMap<>();

    public PenaltyManager(PSHologramm plugin) {
        this.plugin = plugin;
    }

    public void markAttacked(String regionId) {
        lastAttacked.put(regionId, System.currentTimeMillis());
    }

    public boolean hasPenalty(String regionId) {
        if (!lastAttacked.containsKey(regionId)) return false;
        FileConfiguration config = plugin.getConfigManager().getConfig();
        long penaltySeconds = config.getLong("settings.explosion_penalty_time", 300); // По умолчанию 5 минут

        long timePassed = (System.currentTimeMillis() - lastAttacked.get(regionId)) / 1000;
        if (timePassed > penaltySeconds) {
            lastAttacked.remove(regionId);
            return false;
        }
        return true;
    }

    public int getPenaltyMultiplier() {
        return plugin.getConfigManager().getConfig().getInt("settings.explosion_penalty_multiplier", 2);
    }

    public void removeRegion(String regionId) {
        lastAttacked.remove(regionId);
    }
}
