package org.qweyns.qweprotectstones.regions.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.qweyns.qweprotectstones.regions.Region;

public class EffectPurchaseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Region region;
    private final Player player;
    private final String effectName;

    private final int amplifier;

    private boolean cancelled;

    public EffectPurchaseEvent(Region region, Player player, String effectName, int amplifier) {
        this.region = region;
        this.player = player;
        this.effectName = effectName;
        this.amplifier = amplifier;
    }

    public Region getRegion() { return region; }

    public Player getPlayer() { return player; }

    public String getEffectName() { return effectName; }

    public int getAmplifier() { return amplifier; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
