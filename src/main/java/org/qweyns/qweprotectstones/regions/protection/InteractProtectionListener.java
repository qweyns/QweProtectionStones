package org.qweyns.qweprotectstones.regions.protection;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.TrustLevel;
import org.qweyns.qweprotectstones.config.Tunables;
import org.qweyns.qweprotectstones.config.InteractRules;

import java.util.Set;

public class InteractProtectionListener implements Listener {

    private final QweProtectStones plugin;
    private final ProtectionService protection;

    public InteractProtectionListener(QweProtectStones plugin) {
        this.plugin = plugin;
        this.protection = plugin.getProtectionService();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.PHYSICAL) return;

        Region region = protection.regionAt(block.getLocation());
        if (region == null) return;

        // клик по ядру разбирает InteractListener
        if (region.isCore(block.getLocation()) && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Основная рука обработана меню; offhand не должен открывать контейнер-ядро.
            if (!protection.can(region, event.getPlayer(), event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND
                    ? Tunables.TrustAction.MENU : Tunables.TrustAction.CONTAINER)) event.setCancelled(true);
            return;
        }

        InteractRules rules = plugin.getTunables().interactRules();
        if (event.getAction() == Action.PHYSICAL) {
            if (!protection.can(region, event.getPlayer(), rules.forPhysical(block.getType()))) event.setCancelled(true);
            return;
        }

        Material item = event.getItem() == null ? Material.AIR : event.getItem().getType();
        Tunables.TrustAction itemAction = rules.forItem(item);
        if (itemAction != null && !protection.can(region, event.getPlayer(), itemAction)) {
            // сам предмет запрещён здесь (вёдра, огниво, яйца призыва...) — отменяем целиком
            protection.notifyDenied(event.getPlayer(), region, "interact");
            event.setCancelled(true);
            return;
        }

        Tunables.TrustAction blockAction = rules.forBlock(block.getType());
        if (blockAction == null) {
            if (isContainer(block)) blockAction = rules.containers();
            // по камню/земле правый клик ничего не делает: не спамим «нельзя» при еде и стрельбе,
            // установка блока проверяется отдельно в BlockPlaceEvent
            else if (!isInteractable(block.getType())) return;
            else blockAction = rules.defaultBlock();
        }
        if (protection.can(region, event.getPlayer(), blockAction)) return;

        protection.notifyDenied(event.getPlayer(), region, "interact");
        if (rules.keepItemUse() && itemAction == null) {
            // запрещаем только клик по блоку: съесть, выпить, натянуть лук, кинуть жемчуг можно
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDoubleChestOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof org.bukkit.entity.Player player)
                || !(event.getInventory() instanceof org.bukkit.inventory.DoubleChestInventory chest)) return;
        for (var side : java.util.List.of(chest.getLeftSide(), chest.getRightSide())) {
            var location = side.getLocation();
            if (location != null) {
                if (protection.denyInteract(player, location, Tunables.TrustAction.CONTAINER)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean isInteractable(Material type) {
        return type.isInteractable();
    }

    private boolean isContainer(Block block) {
        BlockState state = block.getState(false);
        return state instanceof Container;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Region region = protection.regionAt(entity.getLocation());
        if (region == null) return;

        Tunables.TrustAction required = plugin.getTunables().interactRules().forEntity(entity.getType());

        if (protection.can(region, event.getPlayer(), required)) return;

        protection.notifyDenied(event.getPlayer(), region, "interact");
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (protection.denyInteract(event.getPlayer(), event.getRightClicked().getLocation(), Tunables.TrustAction.ENTITY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLecternBook(PlayerTakeLecternBookEvent event) {
        if (event.getLectern().getLocation() == null) return;

        if (protection.denyInteract(event.getPlayer(), event.getLectern().getLocation(), Tunables.TrustAction.CONTAINER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (protection.denyBuild(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (protection.denyBuild(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }
}
