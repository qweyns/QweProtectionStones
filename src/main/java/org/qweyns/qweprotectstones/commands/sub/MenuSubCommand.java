package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.config.Tunables;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.utils.ColorUtil;

import java.util.List;

/**
 * Открывает GUI привата. Один класс обслуживает menu/upgrade/effects —
 * различаются они только именем файла меню.
 */
public class MenuSubCommand extends AbstractRegionSubCommand {

    private final String commandName;
    private final String menuName;

    public MenuSubCommand(QweProtectStones plugin, String commandName, String menuName) {
        super(plugin);
        this.commandName = commandName;
        this.menuName = menuName;
    }

    @Override
    public String name() {
        return commandName;
    }

    @Override
    public String helpKey() {
        return "help_menu_" + menuName;
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        Region region = regionWithTrust(player, plugin.getProtectionService().requiredFor(Tunables.TrustAction.CONTAINER));
        if (region == null) return;

        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        boolean upgrades = type == null || type.durabilityUpgradeEnabled();

        if (menuName.equals("upgrade") && !upgrades) {
            player.sendMessage(plugin.getLanguageManager().getMessage("upgrades_disabled"));
            return;
        }

        if (!isCloseEnough(player, region)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("too_far_from_block"));
            return;
        }

        // Сервер может подменить встроенное меню своими командами (headless-режим).
        String action = plugin.getConfigManager().getConfig()
                .getString("settings.custom_menus." + menuName + ".action", "MENU");
        if (action.equalsIgnoreCase("COMMAND")) {
            runCustomCommands(player, region);
            return;
        }

        String target = menuName.equals("main") && !upgrades ? "effects" : menuName;
        plugin.getMenuManager().openMenu(player, target, region);
    }

    /** Меню открывается только рядом с ядром — иначе приват можно было бы качать издалека. */
    private boolean isCloseEnough(Player player, Region region) {
        Location core = region.getCoreLocation();
        if (core == null) return true;

        Location playerLocation = player.getLocation();
        if (playerLocation.getWorld() == null || !playerLocation.getWorld().equals(core.getWorld())) return false;

        double max = plugin.getConfigManager().getMenuCommandRadius() + 0.5;
        return playerLocation.distanceSquared(core) <= max * max;
    }

    private void runCustomCommands(Player player, Region region) {
        ConfigurationSection section = plugin.getConfigManager().getConfig()
                .getConfigurationSection("settings.custom_menus." + menuName);
        if (section == null) return;

        for (String raw : section.getStringList("commands")) {
            String parsed = raw.replace("%player%", player.getName())
                    .replace("%region_id%", region.getShortId());

            if (parsed.startsWith("[console] ")) {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                        parsed.substring("[console] ".length()));
            } else if (parsed.startsWith("[message] ")) {
                player.sendMessage(ColorUtil.formatComponent(parsed.substring("[message] ".length())));
            } else {
                player.performCommand(parsed);
            }
        }
    }

    @Override
    public List<String> complete(CommandSender sender, Player player, String[] args) {
        return List.of();
    }
}
