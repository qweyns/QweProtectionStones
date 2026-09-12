package org.qweyns.qweprotectstones.commands.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Общие помощники админ-команд: поиск привата и игрока, разбор чисел, аудит. */
class AdminSupport {

    private final QweProtectStones plugin;

    AdminSupport(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    Region resolveRegion(Player player, String[] args) {
        if (args.length > 1) return plugin.getRegionManager().getByShortId(args[1]);
        return player == null ? null : plugin.getRegionManager().getRegionAt(player.getLocation());
    }

    Region resolveRegionWithMessage(CommandSender sender, Player player, String[] args) {
        Region region = resolveRegion(player, args);
        if (region == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("region_not_found",
                    "%id%", args.length > 1 ? args[1] : "-"));
        }
        return region;
    }

    OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && cached.getUniqueId() != null ? cached : null;
    }

    int parseInt(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_bad_number", "%value%", "-"));
            return -1;
        }
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_bad_number", "%value%", args[index]));
            return -1;
        }
    }

    Boolean parseBoolean(String value) {
        return switch (value) {
            case "true", "on", "yes", "вкл", "да", "1" -> true;
            case "false", "off", "no", "выкл", "нет", "0" -> false;
            default -> null;
        };
    }

    void auditLog(Region region, CommandSender sender, String action, String detail) {
        String actor = sender.getName();
        if (region != null) {
            plugin.getRegionStorage().log(org.qweyns.qweprotectstones.storage.dao.RegionLogEntry.of(
                    region.getId(), actor, "admin_" + action, detail));
        }
        plugin.getCriticalFileLogger().log("ADMIN_" + action.toUpperCase(Locale.ROOT),
                "by=" + actor
                + (region != null ? " region=" + region.getShortId() : "")
                + (detail == null || detail.isBlank() ? "" : " " + detail));
    }

    List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
        return names;
    }

    List<String> filter(List<String> candidates, String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();

        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lowered)) result.add(candidate);
        }
        return result;
    }
}
