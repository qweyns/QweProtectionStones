package org.qweyns.qweprotectstones.regions.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.qweyns.qweprotectstones.regions.Region;

public class RegionDeleteEvent extends Event implements Cancellable {

    public enum Reason {

        BROKEN,

        DESTROYED_BY_RAID,

        COMMAND,

        ADMIN,

        EXPIRED
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Region region;
    private final Player player;
    private final Reason reason;
    private boolean cancelled;

    public RegionDeleteEvent(Region region, Player player, Reason reason) {
        this.region = region;
        this.player = player;
        this.reason = reason;
    }

    public Region getRegion() { return region; }

    public Player getPlayer() { return player; }

    public Reason getReason() { return reason; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
