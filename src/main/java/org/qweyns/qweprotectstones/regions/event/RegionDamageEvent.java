package org.qweyns.qweprotectstones.regions.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.qweyns.qweprotectstones.regions.Region;

public class RegionDamageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Region region;
    private final String explosionType;
    private int damage;
    private boolean cancelled;

    public RegionDamageEvent(Region region, String explosionType, int damage) {
        this.region = region;
        this.explosionType = explosionType;
        this.damage = damage;
    }

    public Region getRegion() { return region; }

    public String getExplosionType() { return explosionType; }

    public int getDamage() { return damage; }

    public void setDamage(int damage) { this.damage = Math.max(0, damage); }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
