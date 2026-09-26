package org.qweyns.qweprotectstones.regions.protection;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.config.Tunables;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RegionMovementListener implements Listener {

    private final QweProtectStones plugin;
    private final ProtectionService protection;

    private final Map<UUID, UUID> currentRegion = new ConcurrentHashMap<>();

    public RegionMovementListener(QweProtectStones plugin) {
        this.plugin = plugin;
        this.protection = plugin.getProtectionService();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;

        Player player = event.getPlayer();
        Region to = protection.regionAt(event.getTo());
        if (to == null) return;
        // Уже внутри (забанили, выключили флаг, приват создан вокруг) — не запираем:
        // игрок может свободно выйти, запрещён только вход снаружи.
        if (to.equals(protection.regionAt(event.getFrom()))) return;

        if (!canEnter(player, to)) {
            event.setCancelled(true);
            protection.sendThrottled(player, plugin.getLanguageManager().getMessage("region_entry_denied"));
            return;
        }


    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Region to = protection.regionAt(event.getTo());
        if (to == null) {
            return;
        }

        // Телепорт внутри привата, где игрок уже находится (жемчуг, хорус), — не «вход снаружи».
        if (to.equals(protection.regionAt(event.getFrom()))) return;

        Player player = event.getPlayer();
        boolean trusted = protection.can(to, player, Tunables.TrustAction.ENTRY);

        if (!trusted && (!protection.flag(to, RegionFlag.TELEPORT_IN) || !canEnter(player, to))) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLanguageManager().getMessage("region_teleport_denied"));
            return;
        }


    }

    // Запрет проверяем до MONITOR, но кеш/сообщения меняем только для принятого события.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMoveApplied(PlayerMoveEvent event) {
        if (event.hasChangedBlock()) handleTransition(event.getPlayer(), protection.regionAt(event.getTo()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleportApplied(PlayerTeleportEvent event) {
        handleTransition(event.getPlayer(), protection.regionAt(event.getTo()));
    }

    /** Регион по последнему перемещению — потокобезопасно для асинхронных вызовов PAPI. */
    public Region currentRegionOf(Player player) {
        UUID id = currentRegion.get(player.getUniqueId());
        return id == null ? null : plugin.getRegionManager().getById(id);
    }

    public boolean canEnter(Player player, Region region) {

        if (protection.isBanned(region, player)) return false;
        if (protection.can(region, player, Tunables.TrustAction.ENTRY)) return true;

        return protection.flag(region, RegionFlag.ENTRY);
    }

    private void handleTransition(Player player, Region to) {
        UUID playerId = player.getUniqueId();
        UUID previousId = currentRegion.get(playerId);
        UUID newId = to == null ? null : to.getId();

        if (java.util.Objects.equals(previousId, newId)) return;

        if (newId == null) currentRegion.remove(playerId);
        else currentRegion.put(playerId, newId);

        Region previous = previousId == null ? null : plugin.getRegionManager().getById(previousId);
        var tunables = plugin.getTunables();

        // шаблон не парсится, если канал выключен (NONE)
        if (previous != null && protection.flag(previous, RegionFlag.GREETING)
                && tunables.regionLeaveEnabled() && tunables.regionLeaveChannel().equals("CHAT")) {
            player.sendMessage(transitionText(previous, "region_leave"));
        }
        if (to != null && protection.flag(to, RegionFlag.GREETING)
                && tunables.regionEnterEnabled() && tunables.regionEnterChannel().equals("CHAT")) {
            player.sendMessage(transitionText(to, "region_enter"));
        }
    }

    private Component transitionText(Region region, String fallbackKey) {
        return plugin.getLanguageManager().getMessage(fallbackKey,
                "%owner%", region.getOwnerName(),
                "%name%", region.getLabel(),
                "%player%", region.getOwnerName(),
                "%type%", typeName(region));
    }

    private String typeName(Region region) {
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        return type != null ? type.displayName() : region.getTypeId();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Region region = protection.regionAt(event.getPlayer().getLocation());
        if (region != null) currentRegion.put(event.getPlayer().getUniqueId(), region.getId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        currentRegion.remove(event.getPlayer().getUniqueId());
    }
}
