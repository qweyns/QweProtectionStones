package org.qweyns.qweprotectstones.listeners;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.TrustLevel;
import org.qweyns.qweprotectstones.utils.ColorUtil;

import java.util.List;

/** Клик по блоку-ядру: открытие меню привата или свои команды из конфига. */
public class RegionInteractListener implements Listener {

    private final QweProtectStones plugin;

    public RegionInteractListener(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Region region = plugin.getRegionManager().getRegionAt(block.getLocation());
        if (region == null || !region.isCore(block.getLocation())) return;

        Player player = event.getPlayer();
        boolean trusted = plugin.getProtectionService().has(region, player, TrustLevel.ACCESS);

        // Яйцо призыва: если тип это разрешает, пропускаем ванильное поведение
        // и меню не открываем — иначе получалось бы и то, и другое сразу.
        ItemStack item = event.getItem();
        if (item != null && item.getType().name().endsWith("_SPAWN_EGG")) {
            RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
            if (type != null && type.spawnEggAllowed() && trusted) {
                event.setUseInteractedBlock(Event.Result.ALLOW);
                event.setUseItemInHand(Event.Result.ALLOW);
            }
            return;
        }

        if (!trusted) return;

        event.setCancelled(true);

        // Shift по ядру ничего не открывает — так удобнее ставить блоки рядом.
        if (player.isSneaking()) return;

        String action = plugin.getConfigManager().getConfig().getString("settings.custom_menus.main.action", "MENU");
        if (action.equalsIgnoreCase("COMMAND")) {
            runCustomCommands(player, region);
            return;
        }

        plugin.getMenuManager().openMenu(player, plugin.getMenuManager().defaultMenuFor(region), region);
    }

    private void runCustomCommands(Player player, Region region) {
        List<String> commands = plugin.getConfigManager().getConfig()
                .getStringList("settings.custom_menus.main.commands");

        for (String raw : commands) {
            String parsed = raw.replace("%player%", player.getName())
                    .replace("%region_id%", region.getShortId());

            if (parsed.startsWith("[console] ")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed.substring("[console] ".length()));
            } else if (parsed.startsWith("[message] ")) {
                player.sendMessage(ColorUtil.formatComponent(parsed.substring("[message] ".length())));
            } else {
                player.performCommand(parsed);
            }
        }
    }
}
