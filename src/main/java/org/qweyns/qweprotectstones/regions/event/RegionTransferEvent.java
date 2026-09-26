package org.qweyns.qweprotectstones.regions.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.qweyns.qweprotectstones.regions.Region;

import java.util.UUID;

public class RegionTransferEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Region region;
    private final Player actor;
    private final UUID oldOwnerId;
    private final String oldOwnerName;
    private final UUID newOwnerId;
    private final String newOwnerName;

    private boolean cancelled;

    public RegionTransferEvent(Region region, Player actor,
                               UUID oldOwnerId, String oldOwnerName,
                               UUID newOwnerId, String newOwnerName) {
        this.region = region;
        this.actor = actor;
        this.oldOwnerId = oldOwnerId;
        this.oldOwnerName = oldOwnerName;
        this.newOwnerId = newOwnerId;
        this.newOwnerName = newOwnerName;
    }

    public Region getRegion() { return region; }

    public Player getActor() { return actor; }

    public UUID getOldOwnerId() { return oldOwnerId; }

    public String getOldOwnerName() { return oldOwnerName == null ? "" : oldOwnerName; }

    public UUID getNewOwnerId() { return newOwnerId; }

    public String getNewOwnerName() { return newOwnerName == null ? "" : newOwnerName; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
