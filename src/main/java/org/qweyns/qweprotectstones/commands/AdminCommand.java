package org.qweyns.qweprotectstones.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.event.RegionDeleteEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Административная команда с фиксированным именем плагина (/qweprotectstones).
 * Отделена от игровой /ps: у игроков и у администрации разные задачи, разные
 * права и разный автокомплит.
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ACTIONS = List.of(
            "reload", "bypass", "info", "delete", "save", "stats", "export", "cleanup", "help");

    private final QweProtectStones plugin;

    public AdminCommand(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("qweprotectstones.admin")) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        Player player = sender instanceof Player p ? p : null;

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "bypass" -> bypass(sender, player);
            case "info" -> info(sender, player, args);
            case "delete" -> delete(sender, player, args);
            case "save" -> save(sender);
            case "stats" -> stats(sender);
            case "export" -> export(sender);
            case "cleanup" -> cleanup(sender);
            default -> sendUsage(sender, label);
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_usage",
                "%actions%", String.join(", ", ACTIONS)));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_hint",
                "%command%", label,
                "%player_command%", plugin.getConfigManager().getCommandName()));
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

        // Статистика осад: сколько раз атаковали, когда и кто последним.
        String lastAttack = region.getLastAttackAt() == 0
                ? plugin.getLanguageManager().getRawMessage("admin_never")
                : new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date(region.getLastAttackAt()));

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_info_attacks",
                "%count%", String.valueOf(region.getAttackCount()),
                "%last%", lastAttack,
                "%by%", region.getLastAttackerName().isBlank()
                        ? plugin.getLanguageManager().getRawMessage("unknown_owner")
                        : region.getLastAttackerName(),
                "%siege%", plugin.getLanguageManager()
                        .getRawMessage(plugin.isUnderSiege(region) ? "siege_active" : "siege_calm")));
    }

    /** Выгрузка в JSON выполняется асинхронно: файл может быть большим. */
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

    /** Ручной запуск очистки заброшенных приватов, не дожидаясь расписания. */
    private void cleanup(CommandSender sender) {
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
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_deleted",
                "%id%", region.getShortId(), "%owner%", region.getOwnerName()));
    }

    private void save(CommandSender sender) {
        plugin.getRegionStorage().saveAll(plugin.getRegionManager().getAllRegions());
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_saved",
                "%count%", String.valueOf(plugin.getRegionManager().size())));
    }

    private void stats(CommandSender sender) {
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_stats",
                "%regions%", String.valueOf(plugin.getRegionManager().size()),
                "%types%", String.valueOf(plugin.getRegionTypes().all().size())));
    }

    /** Приват по короткому id из аргумента, иначе — тот, в котором стоит администратор. */
    private Region resolveRegion(Player player, String[] args) {
        if (args.length > 1) return plugin.getRegionManager().getByShortId(args[1]);
        return player == null ? null : plugin.getRegionManager().getRegionAt(player.getLocation());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("qweprotectstones.admin")) return List.of();

        if (args.length == 1) return filter(ACTIONS, args[0]);

        if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("info"))) {
            List<String> ids = new ArrayList<>();
            for (Region region : plugin.getRegionManager().getAllRegions()) ids.add(region.getShortId());
            return filter(ids, args[1]);
        }
        return List.of();
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
