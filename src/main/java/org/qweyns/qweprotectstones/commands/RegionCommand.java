package org.qweyns.qweprotectstones.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.commands.sub.AutoAddSubCommand;
import org.qweyns.qweprotectstones.commands.sub.BanSubCommand;
import org.qweyns.qweprotectstones.commands.sub.DecorationSubCommand;
import org.qweyns.qweprotectstones.commands.sub.DeleteSubCommand;
import org.qweyns.qweprotectstones.commands.sub.FindSpotSubCommand;
import org.qweyns.qweprotectstones.commands.sub.FlagSubCommand;
import org.qweyns.qweprotectstones.commands.sub.GlowSubCommand;
import org.qweyns.qweprotectstones.commands.sub.HomeSubCommand;
import org.qweyns.qweprotectstones.commands.sub.InfoSubCommand;
import org.qweyns.qweprotectstones.commands.sub.InviteSubCommand;
import org.qweyns.qweprotectstones.commands.sub.ListSubCommand;
import org.qweyns.qweprotectstones.commands.sub.LogSubCommand;
import org.qweyns.qweprotectstones.commands.sub.ResizeSubCommand;
import org.qweyns.qweprotectstones.commands.sub.SellSubCommand;
import org.qweyns.qweprotectstones.commands.sub.RentSubCommand;
import org.qweyns.qweprotectstones.commands.sub.MembersSubCommand;
import org.qweyns.qweprotectstones.commands.sub.MenuSubCommand;
import org.qweyns.qweprotectstones.commands.sub.TransferSubCommand;
import org.qweyns.qweprotectstones.commands.sub.TrustAllSubCommand;
import org.qweyns.qweprotectstones.commands.sub.TrustSubCommand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RegionCommand extends Command {

    private final QweProtectStones plugin;

    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    private final List<SubCommand> ordered = new ArrayList<>();

    public RegionCommand(QweProtectStones plugin, String name, List<String> aliases) {
        super(name);
        this.plugin = plugin;

        setAliases(aliases);

        setDescription(plugin.getLanguageManager().getRawMessage("command_description"));
        setUsage("/" + name + " help");

        registerDefaults();
    }

    private void registerDefaults() {
        register(new InfoSubCommand(plugin));
        register(new MenuSubCommand(plugin, "menu", "main"));
        register(new MenuSubCommand(plugin, "upgrade", "upgrade"));
        register(new MenuSubCommand(plugin, "effects", "effects"));
        register(new TrustSubCommand(plugin, true));
        register(new TrustSubCommand(plugin, false));
        register(new TrustAllSubCommand(plugin, true));
        register(new TrustAllSubCommand(plugin, false));
        register(new InviteSubCommand(plugin, InviteSubCommand.Mode.INVITE));
        register(new InviteSubCommand(plugin, InviteSubCommand.Mode.ACCEPT));
        register(new InviteSubCommand(plugin, InviteSubCommand.Mode.DENY));
        register(new BanSubCommand(plugin, BanSubCommand.Mode.BAN));
        register(new BanSubCommand(plugin, BanSubCommand.Mode.UNBAN));
        register(new BanSubCommand(plugin, BanSubCommand.Mode.LIST));
        register(new MembersSubCommand(plugin));
        register(new FlagSubCommand(plugin));
        register(new ListSubCommand(plugin));
        register(new HomeSubCommand(plugin));
        register(new TransferSubCommand(plugin));
        register(new DeleteSubCommand(plugin));
        register(new GlowSubCommand(plugin));
        register(new DecorationSubCommand(plugin, DecorationSubCommand.Mode.NAME));
        register(new DecorationSubCommand(plugin, DecorationSubCommand.Mode.GREETING));
        register(new DecorationSubCommand(plugin, DecorationSubCommand.Mode.FAREWELL));
        register(new FindSpotSubCommand(plugin));
        register(new ResizeSubCommand(plugin, ResizeSubCommand.Mode.EXPAND));
        register(new ResizeSubCommand(plugin, ResizeSubCommand.Mode.MOVE));
        register(new SellSubCommand(plugin, SellSubCommand.Mode.SELL));
        register(new SellSubCommand(plugin, SellSubCommand.Mode.BUY));
        register(new RentSubCommand(plugin));
        register(new LogSubCommand(plugin));
        register(new AutoAddSubCommand(plugin, AutoAddSubCommand.Mode.TOGGLE_OR_ADD));
        register(new AutoAddSubCommand(plugin, AutoAddSubCommand.Mode.REMOVE));
        register(new AutoAddSubCommand(plugin, AutoAddSubCommand.Mode.LIST));
    }

    public void register(SubCommand sub) {
        ordered.add(sub);
        subCommands.put(sub.name().toLowerCase(Locale.ROOT), sub);
        for (String alias : sub.aliases()) {
            subCommands.put(alias.toLowerCase(Locale.ROOT), sub);
        }
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String[] args) {
        if (args.length == 0) {
            openDefault(sender);
            return true;
        }

        String key = args[0].toLowerCase(Locale.ROOT);
        if (key.equals("help") || key.equals("?")) {
            sendHelp(sender, label, parsePage(args));
            return true;
        }

        SubCommand sub = subCommands.get(key);
        if (sub == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("unknown_subcommand", "%command%", label));
            return true;
        }

        if (sub.permission() != null && !sender.hasPermission(sub.permission())) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }

        Player player = sender instanceof Player p ? p : null;
        if (sub.playerOnly() && player == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("players_only"));
            return true;
        }

        String[] rest = args.length > 1 ? java.util.Arrays.copyOfRange(args, 1, args.length) : new String[0];
        sub.execute(sender, player, rest);
        return true;
    }

    private void openDefault(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("players_only"));
            return;
        }

        Region region = plugin.getRegionManager().getRegionAt(player.getLocation());
        if (region == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("not_in_region"));
            return;
        }

        plugin.getMenuManager().openMenu(player, plugin.getMenuManager().defaultMenuFor(region), region);
    }

    private void sendHelp(CommandSender sender, String label, int requestedPage) {
        var lm = plugin.getLanguageManager();

        List<SubCommand> visible = new ArrayList<>();
        for (SubCommand sub : ordered) {
            if (sub.permission() == null || sender.hasPermission(sub.permission())) visible.add(sub);
        }

        int pageSize = Math.max(1, plugin.getTunables().helpPageSize());
        int total = Math.max(1, (visible.size() + pageSize - 1) / pageSize);
        int page = Math.min(Math.max(1, requestedPage), total);

        sender.sendMessage(lm.getMessage("help_header",
                "%page%", String.valueOf(page), "%total%", String.valueOf(total)));

        int from = (page - 1) * pageSize;
        int to = Math.min(visible.size(), from + pageSize);
        for (int i = from; i < to; i++) {
            SubCommand sub = visible.get(i);
            sender.sendMessage(lm.getMessage("help_line",
                    "%command%", label,
                    "%sub%", sub.name(),
                    // сырой MiniMessage: getRawMessage отдаёт §x-legacy и ломает цвета в шаблоне

            "%description%", lm.rawTemplate(sub.helpKey())));
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

    private static int parsePage(String[] args) {
        if (args.length < 2) return 1;
        try {
            return Math.max(1, Integer.parseInt(args[1]));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private int helpPages(CommandSender sender) {
        int visible = 0;
        for (SubCommand sub : ordered) {
            if (sub.permission() == null || sender.hasPermission(sub.permission())) visible++;
        }
        int pageSize = Math.max(1, plugin.getTunables().helpPageSize());
        return Math.max(1, (visible + pageSize - 1) / pageSize);
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String[] args) {
        Player player = sender instanceof Player p ? p : null;

        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();

            for (SubCommand sub : ordered) {
                if (sub.permission() != null && !sender.hasPermission(sub.permission())) continue;
                if (sub.name().startsWith(prefix)) result.add(sub.name());
            }
            if ("help".startsWith(prefix)) result.add("help");
            return result;
        }

        String key = args[0].toLowerCase(Locale.ROOT);
        if ((key.equals("help") || key.equals("?")) && args.length == 2) {
            List<String> pages = new ArrayList<>();
            for (int i = 1; i <= helpPages(sender); i++) pages.add(String.valueOf(i));
            return pages;
        }

        SubCommand sub = subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) return List.of();
        if (sub.permission() != null && !sender.hasPermission(sub.permission())) return List.of();

        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        return sub.complete(sender, player, rest);
    }
}
