package org.qweyns.qweprotectstones.features.visual;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;
import org.qweyns.qweprotectstones.regions.RegionType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Подсказка в actionbar, когда блок-ядро в руке: влезет ли здесь приват
 * и не пересечётся ли он с чужим.
 *
 * <p>Подсветка границ частицами при взятии в руку убрана как лишняя;
 * осталась только текстовая подсказка — и она по умолчанию выключена
 * ({@code settings.preview-messages}).</p>
 */
public class RegionPreviewListener implements Listener {

    private final QweProtectStones plugin;

    /** Какой тип показывали игроку последним — чтобы не спамить одно и то же. */
    private final Map<UUID, String> showing = new ConcurrentHashMap<>();

    public RegionPreviewListener(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    private boolean isEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("settings.preview-messages", false);
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        RegionType type = item == null ? null : plugin.getRegionTypes().byMaterial(item.getType());

        if (type == null) {
            showing.remove(player.getUniqueId());
            return;
        }

        // Одно сообщение, пока игрок держит тот же тип блока.
        String previous = showing.put(player.getUniqueId(), type.id());
        if (type.id().equals(previous)) return;

        preview(player, type);
    }

    private void preview(Player player, RegionType type) {
        Location at = player.getLocation();
        World world = at.getWorld();
        if (world == null) return;

        RegionBounds bounds = RegionBounds.around(at.getBlockX(), at.getBlockY(), at.getBlockZ(),
                type.radiusX(), type.radiusY(), type.radiusZ(),
                world.getMinHeight(), world.getMaxHeight() - 1);

        Region blocking = plugin.getRegionManager().findOverlapping(world, bounds);

        if (blocking != null) {
            player.sendActionBar(plugin.getLanguageManager().getMessage("preview_blocked",
                    "%owner%", blocking.getOwnerName()));
        } else {
            player.sendActionBar(plugin.getLanguageManager().getMessage("preview_free",
                    "%size%", type.widthX() + "x" + type.widthZ()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        showing.remove(event.getPlayer().getUniqueId());
    }

    public void clear() {
        showing.clear();
    }
}
