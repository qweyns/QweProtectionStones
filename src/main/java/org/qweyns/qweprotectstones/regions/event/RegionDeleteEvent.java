package org.qweyns.qweprotectstones.regions.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.qweyns.qweprotectstones.regions.Region;

/** Вызывается перед удалением привата. */
public class RegionDeleteEvent extends Event implements Cancellable {

    /** Почему приват удаляется — влияет на возврат блока и эффекты. */
    public enum Reason {
        /** Владелец или доверенный игрок сломал ядро. */
        BROKEN,
        /** Прочность закончилась под взрывами. */
        DESTROYED_BY_RAID,
        /** Команда /region delete. */
        COMMAND,
        /** Административное удаление. */
        ADMIN,
        /** Автоочистка заброшенных приватов. */
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

    /** Игрок, инициировавший удаление, или {@code null} для автоматических причин. */
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
