package org.qweyns.qweprotectstones.features.invite;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class InviteManager {

    public record Invite(UUID regionId, UUID inviterId, String inviterName, TrustLevel level) {
    }

    private final QweProtectStones plugin;
    private final Cache<UUID, Invite> pending;

    public InviteManager(QweProtectStones plugin) {
        this.plugin = plugin;
        this.pending = CacheBuilder.newBuilder()
                .expireAfterWrite(expireSeconds(), TimeUnit.SECONDS)
                .build();
    }

    private long expireSeconds() {
        long seconds = plugin.getConfigManager().getConfig().getLong("settings.invite_expire_seconds", 120L);
        return seconds > 0 ? seconds : 120L;
    }

    public boolean invite(Player inviter, Player target, Region region, TrustLevel level) {
        Invite existing = pending.getIfPresent(target.getUniqueId());
        if (existing != null && existing.regionId().equals(region.getId())) return false;

        pending.put(target.getUniqueId(), new Invite(region.getId(), inviter.getUniqueId(), inviter.getName(), level));
        return true;
    }

    public Invite consume(Player target) {
        Invite invite = pending.getIfPresent(target.getUniqueId());
        if (invite != null) pending.invalidate(target.getUniqueId());
        return invite;
    }

    public void cancel(Player target) {
        pending.invalidate(target.getUniqueId());
    }

    public long expireSecondsForMessage() {
        return expireSeconds();
    }

    public void clear() {
        pending.invalidateAll();
    }
}
