package org.qweyns.qweprotectstones.regions.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.util.UUID;

public class RegionMemberChangeEvent extends Event implements Cancellable {

    public enum Action {

        TRUST,

        UNTRUST,

        BAN,

        UNBAN
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Region region;

    private final Player actor;
    private final UUID targetUuid;
    private final String targetName;
    private final Action action;

    private final TrustLevel trust;

    private boolean cancelled;

    public RegionMemberChangeEvent(Region region, Player actor, UUID targetUuid, String targetName,
                                   Action action, TrustLevel trust) {
        this.region = region;
        this.actor = actor;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.action = action;
        this.trust = trust;
    }

    public Region getRegion() { return region; }

    public Player getActor() { return actor; }

    public UUID getTargetUuid() { return targetUuid; }

    public String getTargetName() { return targetName == null ? "" : targetName; }

    public Action getAction() { return action; }

    public TrustLevel getTrust() { return trust; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
