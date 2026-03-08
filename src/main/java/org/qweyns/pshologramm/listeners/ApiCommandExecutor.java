package org.qweyns.pshologramm.listeners;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.models.RegionData;

public class ApiCommandExecutor implements CommandExecutor {
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
            if (current < max) {
                rd.setDurability(current + 1);
                plugin.getStorageManager().saveRegion(rd);
                plugin.getHologramManager().createOrUpdateHologram(
                        regionId, null, rd.getType(), rd.getOwner(), rd.getDurability(), max
                );
            }
        }
        else if (action.equals("add_effect") && args.length >= 4) {
            String effectName = args[2];
            int amp = Integer.parseInt(args[3]);
            plugin.getEffectManager().addCustomEffect(regionId, effectName, amp);
        }

        return true;
    }
}
