package org.qweyns.pshologramm.listeners;

import dev.espi.protectionstones.PSRegion;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.qweyns.pshologramm.PSHologramm;

import java.util.List;

public class RegionInteractListener implements Listener {
    private final PSHologramm plugin;

    public RegionInteractListener(PSHologramm plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRegionInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        PSRegion region = PSRegion.fromLocation(block.getLocation());
        if (region == null || !region.getProtectBlock().getLocation().equals(block.getLocation())) return;

        Player player = event.getPlayer();
        if (!region.isOwner(player.getUniqueId()) && !region.isMember(player.getUniqueId())) return;

        if (player.isSneaking()) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        String action = plugin.getConfigManager().getConfig().getString("settings.custom_menus.main.action", "MENU");

        if (action.equalsIgnoreCase("COMMAND")) {
            List<String> commands = plugin.getConfigManager().getConfig().getStringList("settings.custom_menus.main.commands");
            for (String cmd : commands) {
                String parsedCmd = cmd.replace("%player%", player.getName()).replace("%region_id%", region.getId());
                if (parsedCmd.startsWith("[console] ")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCmd.substring(10));
                } else {
                    player.performCommand(parsedCmd);
                }
            }
        } else {
            if (plugin.getConfigManager().getConfig().getBoolean("regions." + region.getType() + ".enable_durability_upgrade", true)) {
                plugin.getMenuManager().openMenu(player, "main", region);
            } else {
                plugin.getMenuManager().openMenu(player, "effects", region);
            }
        }
    }

    @EventHandler
    public void onSpawnEgg(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        org.bukkit.inventory.ItemStack item = event.getItem();
        if (item == null || !item.getType().name().endsWith("_SPAWN_EGG")) return;

        PSRegion region = PSRegion.fromLocation(event.getClickedBlock().getLocation());
        if (region != null && plugin.getConfigManager().isSpawnEggAllowed(region.getType())) {
            Player p = event.getPlayer();
            if (region.isOwner(p.getUniqueId()) || region.isMember(p.getUniqueId()) || p.hasPermission("pshologramm.admin")) {
                event.setUseInteractedBlock(org.bukkit.event.Event.Result.ALLOW);
                event.setUseItemInHand(org.bukkit.event.Event.Result.ALLOW);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) { checkPistons(event.getBlocks(), event); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) { checkPistons(event.getBlocks(), event); }

    private void checkPistons(List<Block> blocks, org.bukkit.event.Cancellable event) {
        for (Block b : blocks) {
            PSRegion reg = PSRegion.fromLocation(b.getLocation());
            if (reg != null && reg.getProtectBlock().getLocation().equals(b.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
