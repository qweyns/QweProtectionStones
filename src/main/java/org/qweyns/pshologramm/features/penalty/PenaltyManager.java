package org.qweyns.pshologramm.features.penalty;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.qweyns.pshologramm.PSHologramm;

import java.util.concurrent.TimeUnit;

public class PenaltyManager {
    private final PSHologramm plugin;

    private final Cache<String, Long> activePenalties;

    public PenaltyManager(PSHologramm plugin) {
        this.plugin = plugin;

        long durationTicks = plugin.getConfigManager().getDamageCooldownTicks();
        long durationMinutes = (durationTicks / 20) / 60;
        if (durationMinutes <= 0) durationMinutes = 60;

        activePenalties = CacheBuilder.newBuilder()
                .expireAfterWrite(durationMinutes, TimeUnit.MINUTES)
                .build();
    }

    public void markAttacked(String regionId) {
        activePenalties.put(regionId, System.currentTimeMillis());
    }

    public boolean hasPenalty(String regionId) {
        return activePenalties.getIfPresent(regionId) != null;
    }

    public void removeRegion(String regionId) {
        activePenalties.invalidate(regionId);
    }

    public int getPenaltyMultiplier() {
        return plugin.getConfigManager().getConfig().getInt("settings.explosion_penalty_multiplier", 2);
    }
}
