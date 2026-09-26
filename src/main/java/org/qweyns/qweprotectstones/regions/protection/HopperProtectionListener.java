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

        // Горячий путь (сотни раз в секунду): без коллекций, один lookup на сторону.
        if (plugin.getTunables().hoppersBlockOutflow() && foreign(event.getSource(), initiator)) {
            event.setCancelled(true);
            return;
        }
        if (plugin.getTunables().hoppersBlockInflow() && foreign(event.getDestination(), initiator)) {
            event.setCancelled(true);
        }
    }

    /** Контейнер (обе половины двойного сундука) в привате, которому инициатор не принадлежит. */
    private boolean foreign(Inventory inventory, Inventory initiator) {
        if (inventory == null) return false;
        if (inventory instanceof org.bukkit.inventory.DoubleChestInventory chest) {
            return foreign(chest.getLeftSide(), initiator) || foreign(chest.getRightSide(), initiator);
        }
        Location at = inventory.getLocation();
        if (at == null) return false;
        Region region = plugin.getRegionManager().getRegionAt(at);
        return region != null && !inside(initiator, region);
    }

    private boolean inside(Inventory inventory, Region region) {
        Location location = inventory.getLocation();
        return location != null && region.contains(location);
    }
}
