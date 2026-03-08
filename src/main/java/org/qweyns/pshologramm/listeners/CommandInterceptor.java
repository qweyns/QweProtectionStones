package org.qweyns.pshologramm.listeners;

import dev.espi.protectionstones.PSRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.qweyns.pshologramm.PSHologramm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CommandInterceptor implements Listener {
    private final PSHologramm plugin;

    private final Set<String> psAliases = new HashSet<>();

    public CommandInterceptor(PSHologramm plugin) {
        this.plugin = plugin;
        loadDynamicAliases();
    }

    private void loadDynamicAliases() {
        PluginCommand cmd = Bukkit.getPluginCommand("ps");
        if (cmd != null) {
            psAliases.add("/" + cmd.getName().toLowerCase());
            for (String alias : cmd.getAliases()) {
                psAliases.add("/" + alias.toLowerCase());
            }
        }
        psAliases.add("/ps");
        psAliases.add("/pstones");
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        String buffer = event.getBuffer();
        String[] args = buffer.split(" ", -1);
        if (args.length == 0) return;

        String mainCmd = args[0].toLowerCase();
        if (!psAliases.contains(mainCmd)) return;

        if (args.length == 2) {
            List<String> subCmds = new ArrayList<>(Arrays.asList("glow", "effects", "autoadd", "autoremove", "autolist"));

            if (event.getSender() instanceof Player p) {
                PSRegion reg = PSRegion.fromLocation(p.getLocation());
                if (reg != null && plugin.getConfigManager().getConfig().getBoolean("regions." + reg.getType() + ".enable_durability_upgrade", true)) {
                    subCmds.add("menu");
                    subCmds.add("upgrade");
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

        boolean isOurCommand = Arrays.asList("glow", "menu", "upgrade", "effects", "autoadd", "autoremove", "autolist").contains(subCmd);
        if (!isOurCommand) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (subCmd.equals("glow")) {
            PSRegion region = getTargetOrStandingRegion(player);
            if (region == null) { player.sendMessage(plugin.getConfigManager().getMessage("not_in_region")); return; }
            boolean isEnabled = plugin.getVisualManager().toggleGlow(player, region);
            player.sendMessage(plugin.getConfigManager().getMessage(isEnabled ? "glow_enabled" : "glow_disabled"));
            return;
        }

        if (subCmd.equals("menu") || subCmd.equals("upgrade") || subCmd.equals("effects")) {
            PSRegion region = getTargetOrStandingRegion(player);
            if (region == null) {
                player.sendMessage(plugin.getConfigManager().getMessage("not_in_region"));
                return;
            }
            if (!region.isOwner(player.getUniqueId()) && !region.isMember(player.getUniqueId())) {
                player.sendMessage(plugin.getConfigManager().getMessage("not_a_member"));
                return;
            }

            boolean durabilityEnabled = plugin.getConfigManager().getConfig().getBoolean("regions." + region.getType() + ".enable_durability_upgrade", true);

            if (durabilityEnabled) {
                Location pLoc = player.getLocation();
                Location bLoc = region.getProtectBlock().getLocation();
                int maxDist = plugin.getConfigManager().getConfig().getInt("settings.menu_command_radius", 6);

                if (pLoc.getWorld() != bLoc.getWorld() || pLoc.distance(bLoc) > maxDist + 0.5) {
                    player.sendMessage(plugin.getConfigManager().getMessage("too_far_from_block"));
                    return;
                }
            }

            if (subCmd.equals("upgrade")) {
                if (durabilityEnabled) plugin.getMenuManager().openMenu(player, "upgrade", region);
                else player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            } else if (subCmd.equals("effects")) {
                plugin.getMenuManager().openMenu(player, "effects", region);
            } else {
                if (durabilityEnabled) plugin.getMenuManager().openMenu(player, "main", region);
                else plugin.getMenuManager().openMenu(player, "effects", region);
            }
            return;
        }

        if (subCmd.equals("autoadd")) {
            if (args.length == 2) plugin.getAutoAddManager().toggle(player);
            else plugin.getAutoAddManager().addPlayer(player, args[2]);
            return;
        }
        if (subCmd.equals("autoremove") && args.length == 3) { plugin.getAutoAddManager().removePlayer(player, args[2]); return; }
        if (subCmd.equals("autolist")) { plugin.getAutoAddManager().showList(player); return; }
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
