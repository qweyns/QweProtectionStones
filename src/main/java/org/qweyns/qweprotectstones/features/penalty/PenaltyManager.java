package org.qweyns.qweprotectstones.features.penalty;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.qweyns.qweprotectstones.QweProtectStones;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PenaltyManager {

    private final QweProtectStones plugin;

    private Cache<UUID, Long> activePenalties;
    private long durationSeconds;

    public PenaltyManager(QweProtectStones plugin) {
        this.plugin = plugin;
        rebuild();
    }

    public void rebuild() {
        long seconds = plugin.getConfigManager().getExplosionPenaltySeconds();
        if (activePenalties != null && seconds == durationSeconds) return;

        Cache<UUID, Long> replacement = CacheBuilder.newBuilder()
                .expireAfterWrite(seconds, TimeUnit.SECONDS)
                .build();

        if (activePenalties != null) replacement.putAll(activePenalties.asMap());

        this.activePenalties = replacement;
        this.durationSeconds = seconds;
    }

    public void markAttacked(UUID regionId) {
        if (regionId != null) activePenalties.put(regionId, System.currentTimeMillis());
    }

    public boolean hasPenalty(UUID regionId) {
        return regionId != null && activePenalties.getIfPresent(regionId) != null;
    }

    public void removeRegion(UUID regionId) {
        if (regionId != null) activePenalties.invalidate(regionId);
    }

    public int activeCount() {
        return activePenalties == null ? 0 : activePenalties.asMap().size();
    }

    public int getPenaltyMultiplier() {
        return plugin.getConfigManager().getExplosionPenaltyMultiplier();
    }
}
