package org.qweyns.qweprotectstones.regions.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.qweyns.qweprotectstones.regions.Region;

/**
 * Приват уже удалён: убран из индекса и поставлен на удаление из базы. Отменить нельзя.
 *
 * <p>В отличие от {@link RegionDeleteEvent} (вызывается до удаления и может его отменить),
 * это событие гарантирует, что удаление состоялось — здесь безопасно чистить данные аддона,
 * возвращать деньги, рассылать сообщения.</p>
 */
public class RegionDeletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Region region;
    private final Player player;
    private final RegionDeleteEvent.Reason reason;

    public RegionDeletedEvent(Region region, Player player, RegionDeleteEvent.Reason reason) {
        this.region = region;
        this.player = player;
        this.reason = reason;
    }

    /** Снимок привата на момент удаления. */
    public Region getRegion() { return region; }

    /** Кто удалил; null — не игрок (осада, автоочистка, консоль). */
    public Player getPlayer() { return player; }

    public RegionDeleteEvent.Reason getReason() { return reason; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
