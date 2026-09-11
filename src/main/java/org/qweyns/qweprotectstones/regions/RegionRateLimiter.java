package org.qweyns.qweprotectstones.regions;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RegionRateLimiter {

    private final QweProtectStones plugin;

    private final Cache<UUID, Long> lastCreation = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    public RegionRateLimiter(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    private long cooldownMillis() {
        return Math.max(0L, plugin.getConfigManager().getConfig().getLong("settings.region_cooldown_seconds", 3L)) * 1000L;
    }

    public long secondsRemaining(Player player) {
        if (player.hasPermission("qweprotectstones.admin")) return 0;

        long cooldown = cooldownMillis();
        if (cooldown <= 0) return 0;

        Long last = lastCreation.getIfPresent(player.getUniqueId());
        if (last == null) return 0;

        long passed = System.currentTimeMillis() - last;
        return passed >= cooldown ? 0 : (cooldown - passed + 999) / 1000;
    }

    public void markCreated(Player player) {
        lastCreation.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void clear() {
        lastCreation.invalidateAll();
    }
}
