package org.qweyns.qweprotectstones.regions.protection;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;

/**
 * Защита от автоматического переноса предметов: воронки, дроперы и
 * вагонетки-воронки, стоящие снаружи, не могут высасывать предметы из чужих
 * контейнеров и засорять их.
 *
 * <p>Правило простое: инициатор переноса должен находиться в том же привате,
 * что и источник (для отбора) или получатель (для вклада). Перенос внутри
 * одного привата не ограничивается никогда.</p>
 *
 * <p>Оба правила включаются отдельно в секции {@code protection.hoppers}
 * config.yml, поэтому сервер может разрешить «дарить» предметы в чужой приват,
 * оставив запрет на кражу.</p>
 */
public class HopperProtectionListener implements Listener {

    private final QweProtectStones plugin;

    public HopperProtectionListener(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // InventoryMoveItemEvent — самый частый ивент плагина: значения
        // уже закэшированы в Tunables при загрузке конфига.
        if (!plugin.getTunables().hoppersEnabled()) return;

        Inventory initiator = event.getInitiator();
        if (initiator == null) return;

        // Отбор из чужого привата: источник внутри, инициатор снаружи.
        if (plugin.getTunables().hoppersBlockOutflow()) {
            Region sourceRegion = regionOf(event.getSource());
            if (sourceRegion != null && !inside(initiator, sourceRegion)) {
                event.setCancelled(true);
                return;
            }
        }

        // Вклад в чужой приват: получатель внутри, инициатор снаружи.
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
