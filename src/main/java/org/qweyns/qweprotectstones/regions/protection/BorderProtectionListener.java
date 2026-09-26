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

public class BorderProtectionListener implements Listener {

    private final QweProtectStones plugin;
    private final ProtectionService protection;

    public BorderProtectionListener(QweProtectStones plugin) {
        this.plugin = plugin;
        this.protection = plugin.getProtectionService();
    }

    /** Раздатчик за границей не должен лить лаву/воду, поджигать и ставить блоки в чужом привате. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDispense(org.bukkit.event.block.BlockDispenseEvent event) {
        if (!plugin.getTunables().borderDispensers()) return;
        org.bukkit.block.Block source = event.getBlock();
        if (source.getType() != org.bukkit.Material.DISPENSER
                || !(source.getBlockData() instanceof org.bukkit.block.data.Directional directional)) return;
        if (!plugin.getTunables().dispenserItemBlocked(event.getItem().getType())) return;

        Region target = protection.regionAt(source.getRelative(directional.getFacing()).getLocation());
        if (target == null) return;
        Region origin = protection.regionAt(source.getLocation());
        if (target.equals(origin)) return;
        if (origin != null && origin.getOwnerId() != null && target.isOwner(origin.getOwnerId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        Region region = protection.regionAt(event.getBlock().getLocation());
        if (region == null) return;

        if (event.getEntity() instanceof Player player) {
            if (!plugin.getTunables().borderFrostWalker()) return;

            if (!protection.can(region, player, Tunables.TrustAction.BUILD)) {
                protection.notifyDenied(player, region);
                event.setCancelled(true);
            }
            return;
        }

        if (!plugin.getTunables().borderMobTrails()) return;
        if (!protection.flag(region, RegionFlag.MOB_GRIEFING)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent event) {
        if (!plugin.getTunables().borderBonemeal()) return;

        Player player = event.getPlayer();
        Region origin = protection.regionAt(event.getBlock().getLocation());
        for (BlockState state : event.getBlocks()) {
            Region region = protection.regionAt(state.getLocation());

            if (region == null) continue;
            boolean denied = region.isCore(state.getLocation()) || !protection.flag(region, RegionFlag.BLOCK_GROWTH)
                    || (player == null ? !region.equals(origin)
                    : !protection.can(region, player, Tunables.TrustAction.BUILD));
            if (denied) {
                if (player != null) protection.notifyDenied(player, region);
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!plugin.getTunables().borderFishing()) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;

        Entity caught = event.getCaught();
        if (caught == null) return;

        Region region = protection.regionAt(caught.getLocation());
        if (region == null) return;

        Player fisher = event.getPlayer();

        if (caught instanceof Item) {
            if (protection.can(region, fisher, Tunables.TrustAction.INTERACT)) return;
            if (protection.flag(region, RegionFlag.ITEM_PICKUP)) return;
        } else {
            if (protection.can(region, fisher, Tunables.TrustAction.ENTITY)) return;
        }

        protection.notifyDenied(fisher, region, "interact");
        event.setCancelled(true);
    }
}
