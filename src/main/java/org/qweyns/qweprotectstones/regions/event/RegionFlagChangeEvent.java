package org.qweyns.qweprotectstones.regions.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionFlag;

/**
 * Вызывается перед сменой флага привата ({@code /ps flag}).
 *
 * <p>Отмена события оставляет флаг прежним — например, плагин кланов может
 * запретить включать PvP на территории, привязанной к клановой базе.</p>
 */
public class RegionFlagChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Region region;
    private final Player player;
    private final RegionFlag flag;
    /** Прежнее переопределение; {@code null} — флаг не был задан и брался из типа. */
    private final Boolean oldValue;
    /** Новое значение; {@code null} — игрок сбрасывает флаг к значению типа. */
    private final Boolean newValue;

    private boolean cancelled;

    public RegionFlagChangeEvent(Region region, Player player, RegionFlag flag, Boolean oldValue, Boolean newValue) {
        this.region = region;
        this.player = player;
        this.flag = flag;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public Region getRegion() { return region; }

    /** Кто меняет флаг; {@code null} — изменил плагин или консоль. */
    public Player getPlayer() { return player; }

    public RegionFlag getFlag() { return flag; }

    public Boolean getOldValue() { return oldValue; }

    public Boolean getNewValue() { return newValue; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
