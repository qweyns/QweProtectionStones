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

/**
 * Вход и выход из привата: приветствия, флаг ENTRY и запрет телепорта внутрь.
 *
 * <p>Обработчик движения вызывается тысячи раз в секунду, поэтому вся работа
 * идёт только при реальной смене блока, а состояние игрока кэшируется.</p>
 */
public class RegionMovementListener implements Listener {

    private final QweProtectStones plugin;
    private final ProtectionService protection;

    /** Последний известный приват игрока — чтобы не дёргать индекс на каждый тик. */
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

        if (to != null && !canEnter(player, to)) {
            // Не пускаем внутрь: возвращаем игрока на прежнюю позицию.
            event.setCancelled(true);
            player.sendActionBar(plugin.getLanguageManager().getMessage("region_entry_denied", "%owner%", to.getOwnerName()));
            return;
        }

        handleTransition(player, to);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Region to = protection.regionAt(event.getTo());
        if (to == null) {
            handleTransition(event.getPlayer(), null);
            return;
        }

        Player player = event.getPlayer();
        boolean trusted = protection.has(to, player, protection.requiredFor(Tunables.TrustAction.INTERACT));

        if (!trusted && (!protection.flag(to, RegionFlag.TELEPORT_IN) || !canEnter(player, to))) {
            event.setCancelled(true);
            player.sendActionBar(plugin.getLanguageManager().getMessage("region_teleport_denied", "%owner%", to.getOwnerName()));
            return;
        }

        handleTransition(player, to);
    }

    private boolean canEnter(Player player, Region region) {
        // Бан не обходится ни флагом ENTRY, ни публичным доступом.
        if (protection.isBanned(region, player)) return false;
        if (protection.has(region, player, protection.requiredFor(Tunables.TrustAction.INTERACT))) return true;

        return protection.flag(region, RegionFlag.ENTRY);
    }

    /** Сообщения о входе и выходе показываем только при реальной смене привата. */
    private void handleTransition(Player player, Region to) {
        UUID playerId = player.getUniqueId();
        UUID previousId = currentRegion.get(playerId);
        UUID newId = to == null ? null : to.getId();

        if (java.util.Objects.equals(previousId, newId)) return;

        if (newId == null) currentRegion.remove(playerId);
        else currentRegion.put(playerId, newId);

        Region previous = previousId == null ? null : plugin.getRegionManager().getById(previousId);
        if (previous != null && protection.flag(previous, RegionFlag.GREETING)) {
            sendTransition(player, transitionText(previous, previous.getFarewell(), false, "region_leave"), false);
        }
        if (to != null && protection.flag(to, RegionFlag.GREETING)) {
            sendTransition(player, transitionText(to, to.getGreeting(), true, "region_enter"), true);
        }
    }

    /**
     * Куда писать сообщение о входе или выходе — секция region-messages
     * в config.yml: CHAT (в чат), ACTIONBAR (полоска над хотбаром)
     * или NONE (не показывать). Отдельный мастер-выключатель для
     * входа и выхода — enabled.
     */
    private void sendTransition(Player player, Component message, boolean entering) {
        // Каналы закэшированы в Tunables — здесь только выбор ветки.
        var tunables = plugin.getTunables();
        if (entering) {
            if (!tunables.regionEnterEnabled()) return;
            switch (tunables.regionEnterChannel()) {
                case "CHAT" -> player.sendMessage(message);
                case "ACTIONBAR" -> player.sendActionBar(message);
                default -> { /* NONE и опечатки — сообщение выключено */ }
            }
        } else {
            if (!tunables.regionLeaveEnabled()) return;
            switch (tunables.regionLeaveChannel()) {
                case "CHAT" -> player.sendMessage(message);
                case "ACTIONBAR" -> player.sendActionBar(message);
                default -> { /* NONE и опечатки — сообщение выключено */ }
            }
        }
    }

    /**
     * Порядок такой: текст владельца, затем текст типа привата из regions.yml,
     * и только потом общий шаблон из lang-файла. Плейсхолдеры работают везде.
     */
    private Component transitionText(Region region, String custom, boolean entering, String fallbackKey) {
        String text = custom;
        if (text.isEmpty()) {
            RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
            if (type != null) text = entering ? type.greeting() : type.farewell();
        }
        return renderTransition(region, text, fallbackKey);
    }

    /** Название типа привата (display_name) для плейсхолдера %type%. */
    private String typeName(Region region) {
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        return type != null ? type.displayName() : region.getTypeId();
    }

    private Component renderTransition(Region region, String custom, String fallbackKey) {
        String[] placeholders = {
                "%owner%", region.getOwnerName(),
                "%name%", region.getLabel(),
                "%player%", region.getOwnerName(),
                "%type%", typeName(region)
        };

        return custom.isEmpty()
                ? plugin.getLanguageManager().getMessage(fallbackKey, placeholders)
                : plugin.getLanguageManager().format(custom, placeholders);
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

    public void clear() {
        currentRegion.clear();
    }
}
