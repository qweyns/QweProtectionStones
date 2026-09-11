package org.qweyns.qweprotectstones.regions.protection;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.config.Tunables;

import java.util.List;

public class BlockProtectionListener implements Listener {

    private final ProtectionService protection;

    public BlockProtectionListener(QweProtectStones plugin) {
        this.protection = plugin.getProtectionService();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // ядро не трогаем, это LifecycleListener
        Region region = protection.regionAt(event.getBlock().getLocation());
        if (region != null && region.isCore(event.getBlock().getLocation())) return;

        if (protection.denyBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (protection.denyBuild(event.getPlayer(), event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Region region = protection.regionAt(event.getBlock().getLocation());
        if (region != null && !protection.flag(region, RegionFlag.FIRE_SPREAD)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Region region = protection.regionAt(event.getBlock().getLocation());
        if (region == null) return;

        Player igniter = event.getPlayer();
        if (igniter != null) {

            if (!protection.has(region, igniter, protection.requiredFor(Tunables.TrustAction.BUILD))) {
                protection.notifyDenied(igniter, region);
                event.setCancelled(true);
            }
            return;
        }

        if (event.getCause() != BlockIgniteEvent.IgniteCause.LIGHTNING
                && !protection.flag(region, RegionFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        Region region = protection.regionAt(event.getBlock().getLocation());
        if (region == null) return;

        if (event.getSource().getType() == Material.FIRE) {
            if (!protection.flag(region, RegionFlag.FIRE_SPREAD)) event.setCancelled(true);
        } else if (!protection.flag(region, RegionFlag.BLOCK_GROWTH)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        Region target = protection.regionAt(event.getToBlock().getLocation());
        if (target == null) return;

        Region source = protection.regionAt(event.getBlock().getLocation());
        // ограничиваем только приток снаружи
        if (target.equals(source)) return;

        if (!protection.flag(target, RegionFlag.LIQUID_FLOW_IN)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Region region = protection.regionAt(event.getBlock().getLocation());
        if (region != null && !protection.flag(region, RegionFlag.ICE_AND_SNOW)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        Region region = protection.regionAt(event.getBlock().getLocation());
        if (region != null && !protection.flag(region, RegionFlag.ICE_AND_SNOW)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        Region region = protection.regionAt(event.getBlock().getLocation());
        if (region != null && !protection.flag(region, RegionFlag.BLOCK_GROWTH)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        Region region = protection.regionAt(event.getBlock().getLocation());
        if (region != null && !protection.flag(region, RegionFlag.LEAF_DECAY)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {

        Region origin = protection.regionAt(event.getLocation());
        event.getBlocks().removeIf(state -> {
            Region region = protection.regionAt(state.getLocation());
            return region != null && !region.equals(origin);
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonBlocked(event.getBlock().getLocation(), event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonBlocked(event.getBlock().getLocation(), event.getBlocks())) event.setCancelled(true);
    }

    private boolean pistonBlocked(Location pistonLocation, List<Block> blocks) {
        Region pistonRegion = protection.regionAt(pistonLocation);

        for (Block block : blocks) {
            Region blockRegion = protection.regionAt(block.getLocation());
            if (blockRegion == null) continue;

            if (blockRegion.isCore(block.getLocation())) return true;
            if (blockRegion.equals(pistonRegion)) continue;
            if (!protection.flag(blockRegion, RegionFlag.PISTONS_FROM_OUTSIDE)) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Region region = protection.regionAt(event.getBlock().getLocation());
        if (region == null) return;

        // ядро неуязвимо для мобов, урон только через прочность
        if (region.isCore(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }

        if (event.getEntity() instanceof Player player) {
            if (!protection.has(region, player, protection.requiredFor(Tunables.TrustAction.BUILD))) {
                protection.notifyDenied(player, region);
                event.setCancelled(true);
            }
            return;
        }

        boolean trampling = event.getEntityType() != EntityType.ENDERMAN
                && event.getBlock().getType() == Material.FARMLAND;

        if (trampling) {
            if (!protection.flag(region, RegionFlag.CROP_TRAMPLE)) event.setCancelled(true);
            return;
        }

        if (!protection.flag(region, RegionFlag.MOB_GRIEFING)) event.setCancelled(true);
    }
}
