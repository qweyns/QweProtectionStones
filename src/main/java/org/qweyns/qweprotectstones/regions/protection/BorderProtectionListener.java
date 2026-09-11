package org.qweyns.qweprotectstones.regions.protection;

import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.config.Tunables;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionFlag;

/**
 * Трюки через границу привата: ледоход, костная мука и удочка.
 *
 * <p>Общее у всех трёх: действие начинается на «своей» территории (или вне
 * привата), а его эффект попадает в чужой регион — поэтому обычные проверки
 * блока под рукой их не ловят. Каждая защита включается отдельно в секции
 * {@code protection.border} config.yml.</p>
 */
public class BorderProtectionListener implements Listener {

    private final QweProtectStones plugin;
    private final ProtectionService protection;

    public BorderProtectionListener(QweProtectStones plugin) {
        this.plugin = plugin;
        this.protection = plugin.getProtectionService();
    }

    // ------------------------------------------------------------------
    // Ледоход и следы мобов
    // ------------------------------------------------------------------

    /**
     * Ледоход замораживает воду в чужом привате, снежный голем заметает его
     * снегом. Внимание: {@link EntityBlockFormEvent} наследует
     * {@code BlockFormEvent}, поэтому листенер флага ICE_AND_SNOW в
     * {@code BlockProtectionListener} тоже получает эти события — правила не
     * конфликтуют: там решает флаг, здесь — доверие.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        Region region = protection.regionAt(event.getBlock().getLocation());
        if (region == null) return;

        if (event.getEntity() instanceof Player player) {
            if (!enabled("frost-walker")) return;

            // Ледоход — строительное действие: лёд остаётся в чужом привате.
            if (!protection.has(region, player, protection.requiredFor(Tunables.TrustAction.BUILD))) {
                protection.notifyDenied(player, region);
                event.setCancelled(true);
            }
            return;
        }

        // Следы мобов (снег голема): подчиняются флагу MOB_GRIEFING.
        // Отдельный переключатель позволяет оставить прежнее поведение,
        // когда снег внутри привата решал только флаг ICE_AND_SNOW.
        if (!enabled("mob-trails")) return;
        if (!protection.flag(region, RegionFlag.MOB_GRIEFING)) event.setCancelled(true);
    }

    // ------------------------------------------------------------------
    // Костная мука
    // ------------------------------------------------------------------

    /**
     * Мука, внесённая снаружи, заставляет растения «прорастать» в чужой
     * приват. Прямое нажатие мукой по блоку привата уже закрыто обычной
     * проверкой взаимодействия (BONE_MEAL — предмет уровня BUILD); здесь
     * ловится только рост через границу.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent event) {
        if (!enabled("bonemeal")) return;

        Player player = event.getPlayer();
        if (player == null) return; // диспенсер — не игрок, доверие проверить не к кому

        Region origin = protection.regionAt(event.getBlock().getLocation());
        for (BlockState state : event.getBlocks()) {
            Region region = protection.regionAt(state.getLocation());
            // Блоки вне привата и тот же приват, где внесена мука, не считаются:
            // внутри своего привата игрок уже прошёл все проверки.
            if (region == null || region.equals(origin)) continue;

            if (!protection.has(region, player, protection.requiredFor(Tunables.TrustAction.BUILD))) {
                protection.notifyDenied(player, region);
                event.setCancelled(true);
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Удочка
    // ------------------------------------------------------------------

    /**
     * Крючок перелетает границу и вытаскивает оттуда мобов, вагонетки и
     * выпавшие предметы. Правила те же, что для рук: живности — уровень
     * ENTITY, предметы — INTERACT + флаг ITEM_PICKUP.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!enabled("fishing")) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;

        Entity caught = event.getCaught();
        if (caught == null) return;

        Region region = protection.regionAt(caught.getLocation());
        if (region == null) return;

        Player fisher = event.getPlayer();

        if (caught instanceof Item) {
            if (protection.has(region, fisher, protection.requiredFor(Tunables.TrustAction.INTERACT))) return;
            if (protection.flag(region, RegionFlag.ITEM_PICKUP)) return;
        } else {
            if (protection.has(region, fisher, protection.requiredFor(Tunables.TrustAction.ENTITY))) return;
        }

        protection.notifyDenied(fisher, region);
        event.setCancelled(true);
    }

    private boolean enabled(String feature) {
        return plugin.getConfigManager().getConfig().getBoolean("protection.border." + feature, true);
    }
}
