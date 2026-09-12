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

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        Region region = protection.regionAt(event.getBlock().getLocation());
        if (region == null) return;

        if (event.getEntity() instanceof Player player) {
            if (!plugin.getTunables().borderFrostWalker()) return;

            if (!protection.has(region, player, protection.requiredFor(Tunables.TrustAction.BUILD))) {
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
        if (player == null) return;

        Region origin = protection.regionAt(event.getBlock().getLocation());
        for (BlockState state : event.getBlocks()) {
            Region region = protection.regionAt(state.getLocation());

            if (region == null || region.equals(origin)) continue;

            if (!protection.has(region, player, protection.requiredFor(Tunables.TrustAction.BUILD))) {
                protection.notifyDenied(player, region);
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
            if (protection.has(region, fisher, protection.requiredFor(Tunables.TrustAction.INTERACT))) return;
            if (protection.flag(region, RegionFlag.ITEM_PICKUP)) return;
        } else {
            if (protection.has(region, fisher, protection.requiredFor(Tunables.TrustAction.ENTITY))) return;
        }

        protection.notifyDenied(fisher, region, "interact");
        event.setCancelled(true);
    }
}
