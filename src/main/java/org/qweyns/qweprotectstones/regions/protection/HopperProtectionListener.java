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
            Region sourceRegion = regionOf(event.getSource());
            if (sourceRegion != null && !inside(initiator, sourceRegion)) {
                event.setCancelled(true);
                return;
            }
        }

        if (plugin.getTunables().hoppersBlockInflow()) {
            Region destRegion = regionOf(event.getDestination());
            if (destRegion != null && !inside(initiator, destRegion)) {
                event.setCancelled(true);
            }
        }
    }

    private Region regionOf(Inventory inventory) {
        if (inventory == null) return null;
        Location location = inventory.getLocation();
        return location == null ? null : plugin.getRegionManager().getRegionAt(location);
    }

    private boolean inside(Inventory inventory, Region region) {
        Location location = inventory.getLocation();
        return location != null && region.contains(location);
    }
}
