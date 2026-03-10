package org.qweyns.pshologramm.listeners;

import dev.espi.protectionstones.PSRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.qweyns.pshologramm.PSHologramm;

import java.util.*;

public class CommandInterceptor implements Listener {
    private final PSHologramm plugin;
    private final Set<String> psAliases = new HashSet<>();

    public CommandInterceptor(PSHologramm plugin) {
        this.plugin = plugin;
        loadAliases();
    }

    private void loadAliases() {
        List<String> list = plugin.getConfigManager().getConfig().getStringList("settings.ps_aliases");
        if (list == null || list.isEmpty()) {
            list = Arrays.asList("ps", "pstone");
        }
        for (String alias : list) {
            psAliases.add("/" + alias.toLowerCase());
        }
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        String buffer = event.getBuffer();
        String[] args = buffer.split(" ", -1);
        if (args.length == 0) return;

        String mainCmd = args[0].toLowerCase();
        if (!psAliases.contains(mainCmd)) return;

        if (args.length == 2) {
            List<String> subCmds = new ArrayList<>(Arrays.asList("glow", "autoadd", "autoremove", "autolist"));

            if (event.getSender() instanceof Player p) {
                PSRegion reg = PSRegion.fromLocation(p.getLocation());
                if (reg != null) {
                    if (plugin.getConfigManager().getConfig().getBoolean("regions." + reg.getType() + ".enable_durability_upgrade", true)) {
                        subCmds.add("menu");
                        subCmds.add("upgrade");
                    }
                    subCmds.add("effects");

                    ConfigurationSection customMenus = plugin.getConfigManager().getConfig().getConfigurationSection("settings.custom_menus");
                    if (customMenus != null) {
                        for (String key : customMenus.getKeys(false)) {
                            if (!key.equals("main") && !key.equals("upgrade") && !key.equals("effects")) {
                                subCmds.add(key.toLowerCase());
                            }
                        }
                    }
                }
            }

            List<String> completions = new ArrayList<>(event.getCompletions());
            for (String cmd : subCmds) {
                if (cmd.startsWith(args[1].toLowerCase()) && !completions.contains(cmd)) completions.add(cmd);
            }
            event.setCompletions(completions);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        String[] args = message.split(" ");
        if (args.length < 2) return;

        String mainCmd = args[0].toLowerCase();
        if (!psAliases.contains(mainCmd)) return;

        String subCmd = args[1].toLowerCase();
        Player player = event.getPlayer();

        if (subCmd.equals("glow")) {
            event.setCancelled(true);
            PSRegion region = getTargetOrStandingRegion(player);
            if (region == null) { player.sendMessage(plugin.getLanguageManager().getMessage("not_in_region")); return; }
            boolean isEnabled = plugin.getVisualManager().toggleGlow(player, region);
            player.sendMessage(plugin.getLanguageManager().getMessage(isEnabled ? "glow_enabled" : "glow_disabled"));
            return;
        }
        if (subCmd.equals("autoadd")) { event.setCancelled(true); if (args.length == 2) plugin.getAutoAddManager().toggle(player); else plugin.getAutoAddManager().addPlayer(player, args[2]); return; }
        if (subCmd.equals("autoremove") && args.length == 3) { event.setCancelled(true); plugin.getAutoAddManager().removePlayer(player, args[2]); return; }
        if (subCmd.equals("autolist")) { event.setCancelled(true); plugin.getAutoAddManager().showList(player); return; }

        ConfigurationSection customMenus = plugin.getConfigManager().getConfig().getConfigurationSection("settings.custom_menus");
        boolean isBuiltInGui = Arrays.asList("menu", "upgrade", "effects").contains(subCmd);
        String configSection = subCmd.equals("menu") ? "main" : subCmd;
        boolean isCustomGui = customMenus != null && customMenus.contains(configSection);

        if (isBuiltInGui || isCustomGui) {
            event.setCancelled(true);
            PSRegion region = getTargetOrStandingRegion(player);
            if (region == null) { player.sendMessage(plugin.getLanguageManager().getMessage("not_in_region")); return; }
            if (!region.isOwner(player.getUniqueId()) && !region.isMember(player.getUniqueId())) { player.sendMessage(plugin.getLanguageManager().getMessage("not_a_member")); return; }

            boolean durabilityEnabled = plugin.getConfigManager().getConfig().getBoolean("regions." + region.getType() + ".enable_durability_upgrade", true);
            if (durabilityEnabled) {
                Location pLoc = player.getLocation();
                Location bLoc = region.getProtectBlock().getLocation();
                int maxDist = plugin.getConfigManager().getConfig().getInt("settings.menu_command_radius", 6);
                if (pLoc.getWorld() != bLoc.getWorld() || pLoc.distance(bLoc) > maxDist + 0.5) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("too_far_from_block"));
                    return;
                }
            }

            String action = "COMMAND";
            if (isBuiltInGui) action = plugin.getConfigManager().getConfig().getString("settings.custom_menus." + configSection + ".action", "MENU");

            if (action.equalsIgnoreCase("COMMAND")) {
                List<String> commands = plugin.getConfigManager().getConfig().getStringList("settings.custom_menus." + configSection + ".commands");
                for (String cmdStr : commands) {
                    String parsedCmd = cmdStr.replace("%player%", player.getName()).replace("%region_id%", region.getId());
                    if (parsedCmd.startsWith("[console] ")) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCmd.substring(10));
                    else if (parsedCmd.startsWith("[message] ")) player.sendMessage(org.qweyns.pshologramm.utils.ColorUtil.formatLegacyString(parsedCmd.substring(10)));
                    else player.performCommand(parsedCmd);
                }
                return;
            }

            if (subCmd.equals("upgrade")) {
                if (durabilityEnabled) plugin.getMenuManager().openMenu(player, "upgrade", region);
                else player.sendMessage(plugin.getLanguageManager().getMessage("no_permission"));
            } else if (subCmd.equals("effects")) {
                plugin.getMenuManager().openMenu(player, "effects", region);
            } else if (subCmd.equals("menu")) {
                if (durabilityEnabled) plugin.getMenuManager().openMenu(player, "main", region);
                else plugin.getMenuManager().openMenu(player, "effects", region);
            }
        }
    }

    private PSRegion getTargetOrStandingRegion(Player player) {
        PSRegion reg = PSRegion.fromLocation(player.getLocation());
        if (reg != null) return reg;
        int radius = plugin.getConfigManager().getConfig().getInt("settings.menu_command_radius", 6);
        Block target = player.getTargetBlockExact(radius);
        if (target != null) {
            reg = PSRegion.fromLocation(target.getLocation());
            if (reg != null && reg.getProtectBlock().getLocation().equals(target.getLocation())) return reg;
        }
        return null;
    }
}
