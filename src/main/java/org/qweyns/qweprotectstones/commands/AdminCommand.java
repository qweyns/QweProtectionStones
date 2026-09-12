package org.qweyns.qweprotectstones.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.regions.RegionMember;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.TrustLevel;
import org.qweyns.qweprotectstones.regions.event.RegionDeleteEvent;
import org.qweyns.qweprotectstones.regions.event.RegionEvents;
import org.qweyns.qweprotectstones.regions.event.RegionMemberChangeEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ACTIONS = List.of(
            "reload", "bypass", "info", "delete", "save", "stats", "export", "cleanup",
            "give", "setdurability", "setmax", "settype", "setbounds", "tp",
            "flag", "transfer", "setowner", "ban", "unban", "members", "trust", "untrust",
            "import", "restore", "backup", "debug", "help");

    private static final Set<String> REGION_ACTIONS = Set.of(
            "info", "delete", "setdurability", "setmax", "settype", "setbounds", "tp",
            "flag", "transfer", "setowner", "ban", "unban", "members", "trust", "untrust");

    private final QweProtectStones plugin;

    private final long enabledAt = System.currentTimeMillis();

    public AdminCommand(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission(plugin.getConfigManager().getAdminPermissionPrefix())) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        Player player = sender instanceof Player p ? p : null;
        String action = args[0].toLowerCase(Locale.ROOT);

        if (!allowed(sender, action)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_no_action_permission",
                    "%permission%", permissionNode(action)));
            return true;
        }

        switch (action) {
            case "reload" -> reload(sender);
            case "bypass" -> bypass(sender, player);
            case "info" -> info(sender, player, args);
            case "delete" -> delete(sender, player, args);
            case "save" -> save(sender);
            case "stats" -> stats(sender);
            case "export" -> export(sender);
            case "cleanup" -> cleanup(sender);
            case "give" -> give(sender, args);
            case "setdurability" -> setDurability(sender, player, args);
            case "setmax" -> setMaxDurability(sender, player, args);
            case "settype" -> setType(sender, player, args);
            case "setbounds" -> setBounds(sender, player, args);
            case "tp" -> teleport(sender, player, args);
            case "flag" -> flag(sender, player, args);
            case "transfer" -> transfer(sender, player, args, false);
            case "setowner" -> transfer(sender, player, args, true);
            case "ban" -> ban(sender, player, args, true);
            case "unban" -> ban(sender, player, args, false);
            case "members" -> members(sender, player, args);
            case "trust" -> trust(sender, player, args, true);
            case "untrust" -> trust(sender, player, args, false);
            case "import" -> importRegions(sender, args);
            case "restore" -> restore(sender, args);
            case "backup" -> backup(sender);
            case "debug" -> debug(sender);
            case "help" -> sendHelp(sender, label, parseHelpPage(args));
            default -> sendUsage(sender, label);
        }
        return true;
    }

    private String permissionNode(String action) {
        return plugin.getConfigManager().getAdminPermissionPrefix() + "." + action;
    }

    private boolean allowed(CommandSender sender, String action) {
        if (sender.hasPermission(permissionNode(action))) return true;
        return sender.hasPermission(plugin.getConfigManager().getAdminPermissionPrefix())
                && !plugin.getConfigManager().isAdminRequirePerAction();
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_usage",
                "%actions%", String.join(", ", ACTIONS)));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_hint",
                "%command%", label,
                "%player_command%", plugin.getConfigManager().getCommandName()));
    }

    private static int parseHelpPage(String[] args) {
        if (args.length < 2) return 1;
        try {
            return Math.max(1, Integer.parseInt(args[1]));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void sendHelp(CommandSender sender, String label, int requestedPage) {
        var lm = plugin.getLanguageManager();

        List<String> actions = new ArrayList<>();
        for (String action : ACTIONS) {
            if (!action.equals("help") && allowed(sender, action)) actions.add(action);
        }

        int pageSize = Math.max(1, plugin.getTunables().adminHelpPageSize());
        int total = Math.max(1, (actions.size() + pageSize - 1) / pageSize);
        int page = Math.min(Math.max(1, requestedPage), total);

        sender.sendMessage(lm.getMessage("admin_help_header",
                "%page%", String.valueOf(page), "%total%", String.valueOf(total)));

        int from = (page - 1) * pageSize;
        int to = Math.min(actions.size(), from + pageSize);
        for (int i = from; i < to; i++) {
            String action = actions.get(i);
            sender.sendMessage(lm.getMessage("help_line",
                    "%command%", label,
                    "%sub%", action,
                    "%description%", lm.rawTemplate("admin_help_" + action)));
        }

        if (total > 1) {
            // кнопки сырой строкой, подстановки идут до MM-разбора

            String prev = page > 1
                    ? lm.rawTemplate("help_button_prev", "%command%", label, "%page%", String.valueOf(page - 1))
                    : "";
            String next = page < total
                    ? lm.rawTemplate("help_button_next", "%command%", label, "%page%", String.valueOf(page + 1))
                    : "";
            sender.sendMessage(lm.getMessage("help_footer",
                    "%button-prev%", prev, "%button-next%", next,
                    "%page%", String.valueOf(page), "%total%", String.valueOf(total)));
        }
    }

    private void reload(CommandSender sender) {
        plugin.reloadEverything();
        sender.sendMessage(plugin.getLanguageManager().getMessage("reload_success"));
    }

    private void bypass(CommandSender sender, Player player) {
        if (player == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("players_only"));
            return;
        }

        boolean enabled = plugin.getBypassManager().toggle(player);
        plugin.getCriticalFileLogger().log("BYPASS",
                "player=" + player.getName() + " enabled=" + enabled + " by=" + sender.getName());
        player.sendMessage(plugin.getLanguageManager().getMessage(enabled ? "bypass_enabled" : "bypass_disabled"));
    }

    private void info(CommandSender sender, Player player, String[] args) {
        Region region = resolveRegion(player, args);
        if (region == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("region_not_found",
                    "%id%", args.length > 1 ? args[1] : "-"));
            return;
        }

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_info",
                "%id%", region.getId().toString(),
                "%owner%", region.getOwnerName(),
                "%type%", region.getTypeId(),
                "%world%", region.getWorldName(),
                "%x%", String.valueOf(region.getCoreX()),
                "%y%", String.valueOf(region.getCoreY()),
                "%z%", String.valueOf(region.getCoreZ()),
                "%members%", String.valueOf(region.getMemberCount()),
                "%durability%", region.getDurability() + "/" + region.getMaxDurability()));

        String lastAttack = region.getLastAttackAt() == 0
                ? plugin.getLanguageManager().rawTemplate("admin_never")
                : new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date(region.getLastAttackAt()));

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_info_attacks",
                "%count%", String.valueOf(region.getAttackCount()),
                "%last%", lastAttack,
                "%by%", region.getLastAttackerName().isBlank()
                        ? plugin.getLanguageManager().rawTemplate("unknown_owner")
                        : region.getLastAttackerName(),
                "%siege%", plugin.getLanguageManager()
                        .rawTemplate(plugin.isUnderSiege(region) ? "siege_active" : "siege_calm")));
    }

    private void export(CommandSender sender) {
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_export_started"));

        plugin.getSchedulers().runAsync(() -> {
            try {
                java.io.File file = plugin.getRegionExporter().export();
                plugin.getSchedulers().runNextTick(() -> sender.sendMessage(
                        plugin.getLanguageManager().getMessage("admin_export_done", "%file%", file.getName())));
            } catch (java.io.IOException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Не удалось выгрузить приваты", e);
                plugin.getSchedulers().runNextTick(() -> sender.sendMessage(
                        plugin.getLanguageManager().getMessage("admin_export_failed", "%error%", String.valueOf(e.getMessage()))));
            }
        });
    }

    private void auditLog(Region region, CommandSender sender, String action, String detail) {
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

    private void cleanup(CommandSender sender) {
        plugin.getCriticalFileLogger().log("ADMIN_CLEANUP", "by=" + sender.getName());
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_cleanup_started"));
        plugin.getAbandonedRegionTask().sweep();
    }

    private void delete(CommandSender sender, Player player, String[] args) {
        Region region = resolveRegion(player, args);
        if (region == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("region_not_found",
                    "%id%", args.length > 1 ? args[1] : "-"));
            return;
        }

        if (!plugin.getRegionManager().deleteRegion(region, RegionDeleteEvent.Reason.ADMIN, player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("delete_cancelled"));
            return;
        }

        plugin.getRegionLifecycleListener().cleanupVisuals(region);
        auditLog(region, sender, "delete", "owner=" + region.getOwnerName());
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_deleted",
                "%id%", region.getShortId(), "%owner%", region.getOwnerName()));
    }

    private void save(CommandSender sender) {
        // запись всей базы — не в главном потоке
        java.util.List<org.qweyns.qweprotectstones.regions.Region> regions =
                java.util.List.copyOf(plugin.getRegionManager().getAllRegions());
        plugin.getSchedulers().runAsync(() -> {
            plugin.getRegionStorage().saveAll(regions);
            plugin.getSchedulers().runNextTick(() -> sender.sendMessage(
                    plugin.getLanguageManager().getMessage("admin_saved", "%count%", String.valueOf(regions.size()))));
        });
    }

    private void stats(CommandSender sender) {
        var lm = plugin.getLanguageManager();
        sender.sendMessage(lm.getMessage("admin_stats",
                "%regions%", String.valueOf(plugin.getRegionManager().size()),
                "%types%", String.valueOf(plugin.getRegionTypes().all().size()),
                "%owners%", String.valueOf(plugin.getRegionManager().ownersCount())));

        var market = plugin.getMarketManager();
        sender.sendMessage(lm.getMessage("admin_stats_market",
                "%sales%", String.valueOf(market.salesCount()),
                "%rented%", String.valueOf(market.rentedCount()),
                "%listings%", String.valueOf(market.rentalListingsCount()),
                "%penalties%", String.valueOf(plugin.getPenaltyManager().activeCount())));

        long last = plugin.getRegionStorage().lastFlushMillis();
        String flushAgo = last == 0 ? "-" : String.valueOf((System.currentTimeMillis() - last) / 1000);
        sender.sendMessage(lm.getMessage("admin_stats_cache",
                "%pending%", String.valueOf(plugin.getRegionStorage().pendingCount()),
                "%flush%", flushAgo));

        java.util.Map<String, Long> byType = new java.util.TreeMap<>();
        for (org.qweyns.qweprotectstones.regions.Region region : plugin.getRegionManager().getAllRegions()) {
            byType.merge(region.getTypeId(), 1L, Long::sum);
        }
        StringBuilder typesLine = new StringBuilder();
        for (java.util.Map.Entry<String, Long> entry : byType.entrySet()) {
            if (!typesLine.isEmpty()) typesLine.append(", ");
            typesLine.append(entry.getKey()).append(" — ").append(entry.getValue());
        }
        sender.sendMessage(lm.getMessage("admin_stats_types",
                "%types%", typesLine.isEmpty() ? "-" : typesLine.toString()));
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_give_usage"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_give_player_offline", "%player%", args[1]));
            return;
        }

        RegionType type = plugin.getRegionTypes().byId(args[2].toLowerCase(Locale.ROOT));
        if (type == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_give_type_unknown", "%type%", args[2]));
            return;
        }

        int amount = 1;
        if (args.length > 3) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_bad_number", "%value%", args[3]));
                return;
            }
        }
        int max = plugin.getConfigManager().getAdminGiveMaxAmount();
        if (amount < 1 || amount > max) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_give_max_amount",
                    "%max%", String.valueOf(max)));
            return;
        }

        // не влезло — падает на землю

        boolean tags = plugin.getConfigManager().getConfig().getBoolean("settings.core-item-tags", true);
        ItemStack core = org.qweyns.qweprotectstones.utils.RegionItems.core(
                plugin, type, amount, null, tags || type.restrictObtaining(), true);
        target.getInventory().addItem(core)
                .forEach((slot, leftover) -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));

        plugin.getCriticalFileLogger().log("ADMIN_GIVE",
                "by=" + sender.getName() + " player=" + target.getName()
                        + " type=" + type.id() + " amount=" + amount);
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_give_sent",
                "%player%", target.getName(), "%type%", type.id(), "%amount%", String.valueOf(amount)));
        target.sendMessage(plugin.getLanguageManager().getMessage("admin_give_received",
                "%type%", type.displayName(), "%amount%", String.valueOf(amount)));
    }

    private void setDurability(CommandSender sender, Player player, String[] args) {
        int value = parseInt(sender, args, 2);
        if (value < 0) return;

        Region region = resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        region.setDurability(Math.min(value, region.getMaxDurability()));
        plugin.getRegionStorage().save(region);
        auditLog(region, sender, "setdurability", "value=" + region.getDurability());
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_setdurability_done",
                "%id%", region.getShortId(), "%value%", region.getDurability() + "/" + region.getMaxDurability()));
    }

    private void setMaxDurability(CommandSender sender, Player player, String[] args) {
        int value = parseInt(sender, args, 2);
        if (value < 1) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_bad_number",
                    "%value%", args.length > 2 ? args[2] : "-"));
            return;
        }

        Region region = resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        region.setMaxDurability(value);
        plugin.getRegionStorage().save(region);
        auditLog(region, sender, "setmax", "value=" + value);
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_setmax_done",
                "%id%", region.getShortId(), "%value%", region.getDurability() + "/" + region.getMaxDurability()));
    }

    private void setType(CommandSender sender, Player player, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_settype_usage"));
            return;
        }

        Region region = resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        RegionType type = plugin.getRegionTypes().byId(args[2].toLowerCase(Locale.ROOT));
        if (type == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_give_type_unknown", "%type%", args[2]));
            return;
        }

        Region blocking = plugin.getRegionManager().reapplyTypeBounds(region, type);
        if (blocking != null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_bounds_overlap",
                    "%owner%", blocking.getOwnerName(), "%id%", blocking.getShortId()));
            return;
        }

        region.setTypeId(type.id());
        region.setMaxDurability(type.maxDurability());
        region.setDurability(Math.min(region.getDurability(), type.maxDurability()));

        org.bukkit.World world = region.getWorld();
        if (world != null) {
            world.getBlockAt(region.getCoreX(), region.getCoreY(), region.getCoreZ()).setType(type.material());
        }

        plugin.getRegionStorage().save(region);
        plugin.getHologramManager().createOrUpdateHologram(region);
        if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().update(region);
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().update(region);
        plugin.getVisualManager().showBoundary(region, "create");
        auditLog(region, sender, "settype", "type=" + type.id());

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_settype_done",
                "%id%", region.getShortId(), "%type%", type.id(),
                "%size%", type.widthX() + "x" + type.widthZ()));
    }

    private void setBounds(CommandSender sender, Player player, String[] args) {
        if (args.length < 8) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_setbounds_usage"));
            return;
        }

        int[] v = new int[6];
        for (int i = 0; i < 6; i++) {
            try {
                v[i] = Integer.parseInt(args[2 + i]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_bad_number", "%value%", args[2 + i]));
                return;
            }
        }

        Region region = resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        if (v[0] > v[3] || v[1] > v[4] || v[2] > v[5]) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_setbounds_inverted"));
            return;
        }

        Region blocking = plugin.getRegionManager().updateBounds(region, new RegionBounds(v[0], v[1], v[2], v[3], v[4], v[5]));
        if (blocking != null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_bounds_overlap",
                    "%owner%", blocking.getOwnerName(), "%id%", blocking.getShortId()));
            return;
        }

        plugin.getVisualManager().showBoundary(region, "create");
        if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().update(region);
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().update(region);
        auditLog(region, sender, "setbounds", "size=" + region.getBounds().sizeX() + "x" + region.getBounds().sizeZ());
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_setbounds_done",
                "%id%", region.getShortId(), "%size%", region.getBounds().sizeX() + "x" + region.getBounds().sizeZ()));
    }

    private void teleport(CommandSender sender, Player player, String[] args) {
        if (player == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("players_only"));
            return;
        }

        Region region = resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        org.bukkit.World world = region.getWorld();
        if (world == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_tp_world_unloaded", "%world%", region.getWorldName()));
            return;
        }

        Location target = new Location(world,
                region.getCoreX() + 0.5,
                region.getCoreY() + plugin.getConfigManager().getAdminTeleportOffsetY(),
                region.getCoreZ() + 0.5);
        player.teleportAsync(target);
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_tp_done",
                "%id%", region.getShortId(), "%owner%", region.getOwnerName()));
    }

    private void flag(CommandSender sender, Player player, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_flag_usage"));
            return;
        }

        Region region = resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        var parsedFlag = RegionFlag.parse(args[2]);
        if (parsedFlag.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("flag_unknown", "%flag%", args[2]));
            return;
        }
        RegionFlag flag = parsedFlag.get();

        String raw = args[3].toLowerCase(Locale.ROOT);
        if (raw.equals("reset") || raw.equals("default") || raw.equals("сброс")) {
            if (RegionEvents.fireFlagChange(region, player, flag, null)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("flag_change_cancelled"));
                return;
            }
            region.resetFlag(flag);
        } else {
            Boolean value = parseBoolean(raw);
            if (value == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("flag_bad_value", "%value%", args[3]));
                return;
            }
            if (RegionEvents.fireFlagChange(region, player, flag, value)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("flag_change_cancelled"));
                return;
            }
            region.setFlag(flag, value);
        }

        plugin.getRegionStorage().save(region);
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_flag_done",
                "%id%", region.getShortId(), "%flag%", flag.key(), "%value%", args[3]));
    }

    private void transfer(CommandSender sender, Player player, String[] args, boolean forgetPrevious) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(
                    forgetPrevious ? "admin_setowner_usage" : "admin_transfer_usage"));
            return;
        }

        Region region = resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("player_not_found", "%player%", args[2]));
            return;
        }
        if (region.isOwner(target.getUniqueId())) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_transfer_self", "%player%", target.getName()));
            return;
        }

        if (RegionEvents.fireTransfer(region, player, target.getUniqueId(), target.getName())) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_action_cancelled"));
            return;
        }

        UUID previousOwner = region.getOwnerId();
        plugin.getRegionManager().transferRegion(region, target.getUniqueId(), target.getName());
        if (forgetPrevious && previousOwner != null) {
            region.removeMember(previousOwner);
            plugin.getRegionStorage().save(region);
        }
        if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().update(region);
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().update(region);

        auditLog(region, sender, forgetPrevious ? "setowner" : "transfer", "to=" + target.getName());
        sender.sendMessage(plugin.getLanguageManager().getMessage(
                forgetPrevious ? "admin_setowner_done" : "admin_transfer_done",
                "%id%", region.getShortId(),
                "%old%", previousOwner == null ? "-" : region.getOwnerName(),
                "%new%", target.getName()));
    }

    private void ban(CommandSender sender, Player player, String[] args, boolean add) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(
                    add ? "admin_ban_usage" : "admin_unban_usage"));
            return;
        }

        Region region = resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("player_not_found", "%player%", args[2]));
            return;
        }
        String targetName = target.getName() != null ? target.getName() : args[2];

        if (add) {
            if (RegionEvents.fireMemberChange(region, player, target.getUniqueId(), targetName,
                    RegionMemberChangeEvent.Action.BAN, null)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_action_cancelled"));
                return;
            }
            region.ban(target.getUniqueId(), targetName);
        } else {
            if (!region.isBanned(target.getUniqueId())) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_not_banned", "%player%", targetName));
                return;
            }
            if (RegionEvents.fireMemberChange(region, player, target.getUniqueId(), targetName,
                    RegionMemberChangeEvent.Action.UNBAN, null)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_action_cancelled"));
                return;
            }
            region.unban(target.getUniqueId());
        }

        plugin.getRegionStorage().save(region);
        auditLog(region, sender, add ? "ban" : "unban", "player=" + targetName);
        sender.sendMessage(plugin.getLanguageManager().getMessage(
                add ? "admin_ban_done" : "admin_unban_done",
                "%id%", region.getShortId(), "%player%", targetName));
    }

    private void members(CommandSender sender, Player player, String[] args) {
        Region region = resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_members_header",
                "%id%", region.getShortId(), "%owner%", region.getOwnerName()));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_members_owner", "%player%", region.getOwnerName()));

        if (region.getMemberCount() == 0) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_members_empty"));
        } else {
            for (RegionMember member : region.getMembers()) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_members_entry",
                        "%player%", member.name(),
                        "%level%", plugin.getLanguageManager().rawTemplate("trust_" + member.trust().key())));
            }
        }

        for (var entry : region.getBannedPlayers().values()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_members_banned", "%player%", entry));
        }
    }

    private void trust(CommandSender sender, Player player, String[] args, boolean grant) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(
                    grant ? "admin_trust_usage" : "admin_untrust_usage"));
            return;
        }

        Region region = resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("player_not_found", "%player%", args[2]));
            return;
        }
        if (region.isOwner(target.getUniqueId())) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("trust_owner_immutable"));
            return;
        }
        String targetName = target.getName() != null ? target.getName() : args[2];

        if (grant) {
            TrustLevel level = TrustLevel.BUILD;
            if (args.length > 3) {
                var parsed = TrustLevel.parse(args[3]);
                if (parsed.isEmpty() || parsed.get() == TrustLevel.OWNER) {
                    List<String> names = new ArrayList<>();
                    for (TrustLevel l : TrustLevel.grantable()) names.add(l.key());
                    sender.sendMessage(plugin.getLanguageManager().getMessage("trust_unknown_level",
                            "%levels%", String.join(", ", names)));
                    return;
                }
                level = parsed.get();
            }
            if (RegionEvents.fireMemberChange(region, player, target.getUniqueId(), targetName,
                    RegionMemberChangeEvent.Action.TRUST, level)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_action_cancelled"));
                return;
            }
            region.setMember(target.getUniqueId(), targetName, level);
        } else {
            if (region.getMember(target.getUniqueId()).isEmpty()) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("trust_not_member", "%player%", targetName));
                return;
            }
            if (RegionEvents.fireMemberChange(region, player, target.getUniqueId(), targetName,
                    RegionMemberChangeEvent.Action.UNTRUST, null)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_action_cancelled"));
                return;
            }
            region.removeMember(target.getUniqueId());
        }

        plugin.getRegionStorage().save(region);
        auditLog(region, sender, grant ? "trust" : "untrust", "player=" + targetName);
        sender.sendMessage(plugin.getLanguageManager().getMessage(
                grant ? "admin_trust_done" : "admin_untrust_done",
                "%id%", region.getShortId(), "%player%", targetName,
                "%level%", plugin.getLanguageManager().rawTemplate("trust_build")));
    }

    private void backup(CommandSender sender) {
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_backup_started"));
        plugin.getBackupTask().run();
    }

    private void restore(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_restore_usage"));
            File folder = new File(plugin.getDataFolder(), "exports");
            File[] files = folder.listFiles((dir, name) -> name.startsWith("regions_") && name.endsWith(".json"));
            if (files != null && files.length > 0) {
                StringBuilder list = new StringBuilder();
                for (int i = 0; i < files.length && i < 10; i++) {
                    if (i > 0) list.append(", ");
                    list.append(files[i].getName());
                }
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_restore_files", "%files%", list.toString()));
            }
            return;
        }

        // только имя файла, чтобы ../ не вышел за пределы exports
        String fileName = args[1].replace("..", "").replace('/', '_').replace('\\', '_');
        File file = new File(new File(plugin.getDataFolder(), "exports"), fileName);
        if (!file.isFile()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_restore_not_found", "%file%", fileName));
            return;
        }

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_restore_started", "%file%", fileName));

        // парсим вне основного потока
        plugin.getSchedulers().runAsync(() -> {
            var restorer = new org.qweyns.qweprotectstones.features.importer.RegionRestorer(plugin);
            org.qweyns.qweprotectstones.features.importer.RegionRestorer.Result result;
            try {
                result = restorer.restore(file);
            } catch (java.io.IOException e) {
                plugin.getSchedulers().runNextTick(() -> sender.sendMessage(
                        plugin.getLanguageManager().getMessage("admin_restore_failed", "%error%", String.valueOf(e.getMessage()))));
                return;
            }

            plugin.getSchedulers().runNextTick(() -> {
                if (result.restored() > 0) {
                    if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().redrawAll();
                    if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().updateAll();
                }
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_restore_done",
                        "%restored%", String.valueOf(result.restored()),
                        "%skipped%", String.valueOf(result.skipped()),
                        "%errors%", String.valueOf(result.errors())));
            });
        });
    }

    private void debug(CommandSender sender) {
        long uptimeMinutes = (System.currentTimeMillis() - enabledAt) / 60_000L;
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_debug_header",
                "%version%", plugin.getPluginMeta().getVersion(),
                "%uptime%", String.valueOf(uptimeMinutes)));

        String dbType = plugin.getConfigManager().getConfig().getString("database.type", "SQLITE");
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_debug_storage",
                "%db%", dbType.toUpperCase(Locale.ROOT),
                "%pending%", String.valueOf(plugin.getRegionStorage().pendingCount()),
                "%regions%", String.valueOf(plugin.getRegionManager().size()),
                "%types%", String.valueOf(plugin.getRegionTypes().all().size())));

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_debug_hooks",
                "%vault%", mark(plugin.getVaultHook().isEnabled()),
                "%points%", mark(plugin.getPlayerPointsHook().isEnabled()),
                "%papi%", mark(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null),
                "%dynmap%", mark(plugin.getDynmapIntegration() != null && plugin.getDynmapIntegration().isActive()),
                "%bluemap%", mark(plugin.getBlueMapIntegration() != null && plugin.getBlueMapIntegration().isActive()),
                "%discordsrv%", mark(plugin.getDiscordSrvHook() != null && plugin.getDiscordSrvHook().isActive())));

        Runtime rt = Runtime.getRuntime();
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_debug_runtime",
                "%folia%", plugin.getSchedulers().isFolia() ? "Folia" : "Paper/Spigot",
                "%api%", org.qweyns.qweprotectstones.api.QpsApi.isAvailable() ? "OK" : "OFF",
                "%memory%", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024 + "/" + rt.maxMemory() / 1024 / 1024 + " MB",
                "%threads%", String.valueOf(Thread.activeCount())));

        if (plugin.getConfigManager().getConfig().getBoolean("debug.verbose", false)) {
            for (RegionType type : plugin.getRegionTypes().all()) {
                int count = 0;
                for (Region region : plugin.getRegionManager().getAllRegions()) {
                    if (region.getTypeId().equals(type.id())) count++;
                }
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_debug_type",
                        "%type%", type.id(), "%count%", String.valueOf(count)));
            }
        }
    }

    private String mark(boolean value) {
        return value ? "+" : "-";
    }

    private void importRegions(CommandSender sender, String[] args) {
        String source = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        org.qweyns.qweprotectstones.features.importer.RegionImporter importer =
                new org.qweyns.qweprotectstones.features.importer.RegionImporter(plugin);

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_import_started", "%source%", source));
        org.qweyns.qweprotectstones.features.importer.RegionImporter.Result result;
        switch (source) {
            case "worldguard", "wg" -> result = importer.importFromWorldGuard(false);
            case "protectionstones", "ps" -> result = importer.importFromWorldGuard(true);
            case "griefprevention", "gp" -> result = importer.importFromGriefPrevention();
            default -> {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_import_usage"));
                return;
            }
        }

        importer.refreshMaps();
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_import_done",
                "%imported%", String.valueOf(result.imported()),
                "%skipped%", String.valueOf(result.skipped()),
                "%errors%", String.valueOf(result.errors())));
    }

    private Region resolveRegion(Player player, String[] args) {
        if (args.length > 1) return plugin.getRegionManager().getByShortId(args[1]);
        return player == null ? null : plugin.getRegionManager().getRegionAt(player.getLocation());
    }

    private Region resolveRegionWithMessage(CommandSender sender, Player player, String[] args) {
        Region region = resolveRegion(player, args);
        if (region == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("region_not_found",
                    "%id%", args.length > 1 ? args[1] : "-"));
        }
        return region;
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && cached.getUniqueId() != null ? cached : null;
    }

    private int parseInt(CommandSender sender, String[] args, int index) {
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

    private Boolean parseBoolean(String value) {
        return switch (value) {
            case "true", "on", "yes", "вкл", "да", "1" -> true;
            case "false", "off", "no", "выкл", "нет", "0" -> false;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission(plugin.getConfigManager().getAdminPermissionPrefix())) return List.of();

        if (args.length == 1) return filter(ACTIONS, args[0]);

        String action = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2 && REGION_ACTIONS.contains(action)) {
            List<String> ids = new ArrayList<>();
            for (Region region : plugin.getRegionManager().getAllRegions()) ids.add(region.getShortId());
            return filter(ids, args[1]);
        }

        if (args.length == 2 && action.equals("give")) {
            return filter(onlineNames(), args[1]);
        }

        if (args.length == 2 && action.equals("import")) {
            return filter(List.of("worldguard", "protectionstones", "griefprevention"), args[1]);
        }

        if (args.length == 2 && action.equals("restore")) {
            List<String> names = new ArrayList<>();
            File folder = new File(plugin.getDataFolder(), "exports");
            File[] files = folder.listFiles((dir, name) -> name.startsWith("regions_") && name.endsWith(".json"));
            if (files != null) for (File file : files) names.add(file.getName());
            return filter(names, args[1]);
        }

        if (args.length == 3 && (action.equals("give") || action.equals("settype"))) {
            return filter(new ArrayList<>(plugin.getRegionTypes().ids()), args[2]);
        }

        if (args.length == 3 && action.equals("flag")) {
            List<String> names = new ArrayList<>();
            for (RegionFlag flag : RegionFlag.values()) names.add(flag.key());
            return filter(names, args[2]);
        }

        if (args.length == 3 && (action.equals("transfer") || action.equals("setowner")
                || action.equals("ban") || action.equals("unban")
                || action.equals("trust") || action.equals("untrust"))) {
            return filter(onlineNames(), args[2]);
        }

        if (args.length == 4 && action.equals("flag")) {
            return filter(List.of("true", "false", "reset"), args[3]);
        }

        if (args.length == 4 && action.equals("trust")) {
            List<String> levels = new ArrayList<>();
            for (TrustLevel level : TrustLevel.grantable()) levels.add(level.key());
            return filter(levels, args[3]);
        }

        if (args.length == 4 && action.equals("give")) {
            return filter(List.of(String.valueOf(plugin.getConfigManager().getConfig()
                    .getInt("admin.give.max-amount", 64))), args[3]);
        }

        return List.of();
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
        return names;
    }

    private List<String> filter(List<String> candidates, String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();

        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lowered)) result.add(candidate);
        }
        return result;
    }
}
