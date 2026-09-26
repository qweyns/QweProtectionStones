package org.qweyns.qweprotectstones.regions.protection;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;

public class HopperProtectionListener implements Listener {

    private final QweProtectStones plugin;

    public HopperProtectionListener(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {

        if (!plugin.getTunables().hoppersEnabled()) return;

        Inventory initiator = event.getInitiator();
        if (initiator == null) return;

        if (plugin.getTunables().hoppersBlockOutflow()) {
            for (Region sourceRegion : regionsOf(event.getSource())) {
                if (!inside(initiator, sourceRegion)) { event.setCancelled(true); return; }
            }
        }

        if (plugin.getTunables().hoppersBlockInflow()) {
            for (Region destRegion : regionsOf(event.getDestination())) {
                if (!inside(initiator, destRegion)) { event.setCancelled(true); return; }
            }
        }
    }

    private java.util.Set<Region> regionsOf(Inventory inventory) {
        java.util.Set<Region> result = new java.util.HashSet<>();
        if (inventory == null) return result;
        if (inventory instanceof org.bukkit.inventory.DoubleChestInventory chest) {
            result.addAll(regionsOf(chest.getLeftSide()));
            result.addAll(regionsOf(chest.getRightSide()));
        } else {
            Location at = inventory.getLocation();
            Region region = at == null ? null : plugin.getRegionManager().getRegionAt(at);
            if (region != null) result.add(region);
        }
        return result;
    }

    private boolean inside(Inventory inventory, Region region) {
        Location location = inventory.getLocation();
        return location != null && region.contains(location);
    }
}
