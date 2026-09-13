package org.qweyns.qweprotectstones.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.commands.admin.AdminSupport;
import org.qweyns.qweprotectstones.commands.admin.MemberAdminCommands;
import org.qweyns.qweprotectstones.commands.admin.RegionAdminCommands;
import org.qweyns.qweprotectstones.commands.admin.SystemAdminCommands;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Диспетчер /qweprotectstones: права, справка, tab-подсказки; сами команды — в commands.admin. */
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
    private final AdminSupport support;
    private final RegionAdminCommands regionCommands;
    private final MemberAdminCommands memberCommands;
    private final SystemAdminCommands systemCommands;

    public AdminCommand(QweProtectStones plugin) {
        this.plugin = plugin;
        this.support = new AdminSupport(plugin);
        this.regionCommands = new RegionAdminCommands(plugin, support);
        this.memberCommands = new MemberAdminCommands(plugin, support);
        this.systemCommands = new SystemAdminCommands(plugin);
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
            case "reload" -> systemCommands.reload(sender);
            case "bypass" -> systemCommands.bypass(sender, player);
            case "info" -> regionCommands.info(sender, player, args);
            case "delete" -> regionCommands.delete(sender, player, args);
            case "save" -> systemCommands.save(sender);
            case "stats" -> systemCommands.stats(sender);
            case "export" -> systemCommands.export(sender);
            case "cleanup" -> systemCommands.cleanup(sender);
            case "give" -> regionCommands.give(sender, args);
            case "setdurability" -> regionCommands.setDurability(sender, player, args);
            case "setmax" -> regionCommands.setMaxDurability(sender, player, args);
            case "settype" -> regionCommands.setType(sender, player, args);
            case "setbounds" -> regionCommands.setBounds(sender, player, args);
            case "tp" -> regionCommands.teleport(sender, player, args);
            case "flag" -> regionCommands.flag(sender, player, args);
            case "transfer" -> regionCommands.transfer(sender, player, args, false);
            case "setowner" -> regionCommands.transfer(sender, player, args, true);
            case "ban" -> memberCommands.ban(sender, player, args, true);
            case "unban" -> memberCommands.ban(sender, player, args, false);
            case "members" -> memberCommands.members(sender, player, args);
            case "trust" -> memberCommands.trust(sender, player, args, true);
            case "untrust" -> memberCommands.trust(sender, player, args, false);
            case "import" -> systemCommands.importRegions(sender, args);
            case "restore" -> systemCommands.restore(sender, args);
            case "backup" -> systemCommands.backup(sender);
            case "debug" -> systemCommands.debug(sender);
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

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission(plugin.getConfigManager().getAdminPermissionPrefix())) return List.of();

        if (args.length == 1) return support.filter(ACTIONS, args[0]);

        String action = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2 && REGION_ACTIONS.contains(action)) {
            List<String> ids = new ArrayList<>();
            for (Region region : plugin.getRegionManager().getAllRegions()) ids.add(region.getShortId());
            return support.filter(ids, args[1]);
        }

        if (args.length == 2 && action.equals("give")) {
            return support.filter(support.onlineNames(), args[1]);
        }

        if (args.length == 2 && action.equals("import")) {
            return support.filter(List.of("worldguard", "protectionstones", "griefprevention"), args[1]);
        }

        if (args.length == 2 && action.equals("restore")) {
            List<String> names = new ArrayList<>();
            File folder = new File(plugin.getDataFolder(), "exports");
            File[] files = folder.listFiles((dir, name) -> name.startsWith("regions_") && name.endsWith(".json"));
            if (files != null) for (File file : files) names.add(file.getName());
            return support.filter(names, args[1]);
        }

        if (args.length == 3 && (action.equals("give") || action.equals("settype"))) {
            return support.filter(new ArrayList<>(plugin.getRegionTypes().ids()), args[2]);
        }

        if (args.length == 3 && action.equals("flag")) {
            List<String> names = new ArrayList<>();
            for (RegionFlag flag : RegionFlag.values()) names.add(flag.key());
            return support.filter(names, args[2]);
        }

        if (args.length == 3 && (action.equals("transfer") || action.equals("setowner")
                || action.equals("ban") || action.equals("unban")
                || action.equals("trust") || action.equals("untrust"))) {
            return support.filter(support.onlineNames(), args[2]);
        }

        if (args.length == 4 && action.equals("flag")) {
            return support.filter(List.of("true", "false", "reset"), args[3]);
        }

        if (args.length == 4 && action.equals("trust")) {
            List<String> levels = new ArrayList<>();
            for (TrustLevel level : TrustLevel.grantable()) levels.add(level.key());
            return support.filter(levels, args[3]);
        }

        if (args.length == 4 && action.equals("give")) {
            return support.filter(List.of(String.valueOf(plugin.getConfigManager().getConfig()
                    .getInt("admin.give.max-amount", 64))), args[3]);
        }

        return List.of();
    }
}
