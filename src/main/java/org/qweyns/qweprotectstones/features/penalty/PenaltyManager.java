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

    /** Сколько ещё действует штраф, в виде «м:сс» (или «ч:мм:сс»). */
    public String remainingText(Region region) {
        long left = region == null ? 0 : Math.max(0, region.getPenaltyUntil() - System.currentTimeMillis());
        long seconds = (left + 999) / 1000;
        long h = seconds / 3600, m = seconds % 3600 / 60, s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
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
