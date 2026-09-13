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

import java.util.Set;

public class InteractProtectionListener implements Listener {

    private static final Set<Material> BUILD_LEVEL_BLOCKS = Set.of(
            Material.FARMLAND, Material.TURTLE_EGG, Material.SNIFFER_EGG,
            Material.RESPAWN_ANCHOR, Material.CAKE, Material.DRAGON_EGG,
            Material.SPAWNER, Material.JUKEBOX, Material.COMPOSTER);

    private static final Set<Material> BUILD_LEVEL_ITEMS = Set.of(
            Material.FLINT_AND_STEEL, Material.FIRE_CHARGE, Material.BONE_MEAL,
            Material.ARMOR_STAND, Material.END_CRYSTAL, Material.ITEM_FRAME,
            Material.GLOW_ITEM_FRAME, Material.PAINTING, Material.TNT_MINECART,
            Material.CHEST_MINECART, Material.HOPPER_MINECART, Material.MINECART,
            Material.FURNACE_MINECART, Material.WATER_BUCKET, Material.LAVA_BUCKET);

    private static final Set<EntityType> CONTAINER_ENTITIES = Set.of(
            EntityType.CHEST_MINECART, EntityType.HOPPER_MINECART, EntityType.FURNACE_MINECART);

    private final ProtectionService protection;

    public InteractProtectionListener(QweProtectStones plugin) {
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
        if (region.isCore(block.getLocation()) && event.getAction() == Action.RIGHT_CLICK_BLOCK) return;

        TrustLevel required = protection.requiredFor(requiredActionFor(block, event));
        if (protection.has(region, event.getPlayer(), required)) return;

        if (event.getAction() != Action.PHYSICAL) protection.notifyDenied(event.getPlayer(), region, "interact");
        event.setCancelled(true);
    }

    private Tunables.TrustAction requiredActionFor(Block block, PlayerInteractEvent event) {
        Material type = block.getType();

        if (event.getAction() == Action.PHYSICAL) {

            return type == Material.FARMLAND ? Tunables.TrustAction.BUILD : Tunables.TrustAction.INTERACT;
        }

        Material item = event.getItem() == null ? Material.AIR : event.getItem().getType();
        if (BUILD_LEVEL_ITEMS.contains(item) || item.name().endsWith("_SPAWN_EGG")) return Tunables.TrustAction.BUILD;
        if (BUILD_LEVEL_BLOCKS.contains(type)) return Tunables.TrustAction.BUILD;
        if (isContainer(block)) return Tunables.TrustAction.CONTAINER;
        if (isSimpleAccess(type)) return Tunables.TrustAction.INTERACT;

        // незнакомое = нужен доступ, безопасный дефолт
        return Tunables.TrustAction.CONTAINER;
    }

    private boolean isContainer(Block block) {
        BlockState state = block.getState(false);
        return state instanceof Container;
    }

    private boolean isSimpleAccess(Material type) {
        return Tag.DOORS.isTagged(type)
                || Tag.TRAPDOORS.isTagged(type)
                || Tag.FENCE_GATES.isTagged(type)
                || Tag.BUTTONS.isTagged(type)
                || Tag.BEDS.isTagged(type)
                || type == Material.LEVER
                || type == Material.CRAFTING_TABLE
                || type == Material.ENDER_CHEST
                || type == Material.BELL
                || type == Material.NOTE_BLOCK
                || type == Material.LECTERN;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Region region = protection.regionAt(entity.getLocation());
        if (region == null) return;

        TrustLevel required = protection.requiredFor(CONTAINER_ENTITIES.contains(entity.getType())
                ? Tunables.TrustAction.CONTAINER
                : entityInteractAction(entity));

        if (protection.has(region, event.getPlayer(), required)) return;

        protection.notifyDenied(event.getPlayer(), region, "interact");
        event.setCancelled(true);
    }

    private Tunables.TrustAction entityInteractAction(Entity entity) {
        return switch (entity.getType()) {
            case ITEM_FRAME, GLOW_ITEM_FRAME, ARMOR_STAND, PAINTING -> Tunables.TrustAction.ENTITY;
            case VILLAGER, WANDERING_TRADER -> Tunables.TrustAction.INTERACT;
            default -> Tunables.TrustAction.CONTAINER;
        };
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (protection.denyInteract(event.getPlayer(), event.getRightClicked().getLocation(), protection.requiredFor(Tunables.TrustAction.ENTITY))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLecternBook(PlayerTakeLecternBookEvent event) {
        if (event.getLectern().getLocation() == null) return;

        if (protection.denyInteract(event.getPlayer(), event.getLectern().getLocation(), protection.requiredFor(Tunables.TrustAction.CONTAINER))) {
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
