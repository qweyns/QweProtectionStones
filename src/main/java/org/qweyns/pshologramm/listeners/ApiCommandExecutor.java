package org.qweyns.pshologramm.listeners;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.models.RegionData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ApiCommandExecutor implements CommandExecutor, TabCompleter {
    private final PSHologramm plugin;

    public ApiCommandExecutor(PSHologramm plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (!sender.hasPermission("pshologramm.admin") && !sender.getName().equals("CONSOLE")) {
            return true;
        }

        if (args.length < 2) return true;

        String action = args[0].toLowerCase();
        String regionId = args[1];
        RegionData rd = plugin.getStorageManager().getRegion(regionId);

        if (rd == null) return true;

        if (action.equals("force_upgrade")) {
            int current = rd.getDurability();
            int max = rd.getMaxDurability();

            if (current >= max) {
                return true;
            }

            int amount = 1;
            if (args.length >= 3) {
                if (args[2].equalsIgnoreCase("max")) {
                    amount = max;
                } else {
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {}
                }
            }

            int targetLevel = current + amount;
            if (targetLevel > max) {
                targetLevel = max;
            }

            rd.setDurability(targetLevel);
            plugin.getStorageManager().saveRegion(rd);

            org.bukkit.World world = org.bukkit.Bukkit.getWorld(rd.getWorld());
            if (world != null) {
                org.bukkit.Location loc = new org.bukkit.Location(world, rd.getX(), rd.getY(), rd.getZ());
                plugin.getHologramManager().createOrUpdateHologram(
                        regionId, loc, rd.getType(), rd.getOwner(), rd.getDurability(), max
                );
            }
        }
        else if (action.equals("add_effect") && args.length >= 4) {
            String effectName = args[2].toUpperCase();
            int amp = 0;
            try {
                amp = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {}

            plugin.getEffectManager().addCustomEffect(regionId, effectName, amp);
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("pshologramm.admin")) return completions;

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("force_upgrade", "add_effect");
            for (String s : subCommands) {
                if (s.startsWith(args[0].toLowerCase())) completions.add(s);
            }
            return completions;
        }

        if (args.length == 2) {
            for (String regionId : plugin.getStorageManager().getAllRegions().keySet()) {
                if (regionId.toLowerCase().startsWith(args[1].toLowerCase())) completions.add(regionId);
            }
            return completions;
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("add_effect")) {
                String regionId = args[1];
                RegionData rd = plugin.getStorageManager().getRegion(regionId);
                List<String> allowedEffects;

                if (rd != null) {
                    allowedEffects = plugin.getConfigManager().getAllowedEffects(rd.getType());
                } else {
                    allowedEffects = plugin.getConfigManager().getAllowedEffects("default");
                }

                if (allowedEffects != null) {
                    for (String eff : allowedEffects) {
                        if (eff.toLowerCase().startsWith(args[2].toLowerCase())) completions.add(eff);
                    }
                }
            }
            else if (args[0].equalsIgnoreCase("force_upgrade")) {
                List<String> amounts = Arrays.asList("1", "5", "10", "max");
                for (String amt : amounts) {
                    if (amt.startsWith(args[2].toLowerCase())) completions.add(amt);
                }
            }
            return completions;
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("add_effect")) {
            List<String> levels = Arrays.asList("0", "1", "2", "3");
            for (String lvl : levels) {
                if (lvl.startsWith(args[3])) completions.add(lvl);
            }
            return completions;
        }

        return completions;
    }
}
