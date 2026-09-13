package org.qweyns.qweprotectstones.features.penalty;

import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;

public class PenaltyManager {

    private final QweProtectStones plugin;

    public PenaltyManager(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public void markAttacked(Region region) {
        if (region == null) return;
        // дедлайн, а не таймер: штраф переживает рестарт сервера
        region.setPenaltyUntil(System.currentTimeMillis() + penaltyMillis());
        plugin.getRegionStorage().save(region);
    }

    public boolean hasPenalty(Region region) {
        return region != null && region.getPenaltyUntil() > System.currentTimeMillis();
    }

    public int getPenaltyMultiplier() {
        return plugin.getConfigManager().getExplosionPenaltyMultiplier();
    }

    public int activeCount() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (Region region : plugin.getRegionManager().getAllRegions()) {
            if (region.getPenaltyUntil() > now) count++;
        }
        return count;
    }

    private long penaltyMillis() {
        return plugin.getConfigManager().getExplosionPenaltySeconds() * 1000L;
    }
}
