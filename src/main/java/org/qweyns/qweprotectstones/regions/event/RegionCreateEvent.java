package org.qweyns.qweprotectstones.regions.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.qweyns.qweprotectstones.regions.Region;

/**
 * Вызывается перед регистрацией нового привата. Отмена события отменяет и
 * установку блока-ядра — игрок получит предмет обратно.
 */
public class RegionCreateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Region region;
    private final Player player;
    private boolean cancelled;

    public RegionCreateEvent(Region region, Player player) {
        this.region = region;
        this.player = player;
    }

    public Region getRegion() { return region; }

    public Player getPlayer() { return player; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
