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

/**
 * Главная команда плагина. Имя и алиасы берутся из config.yml, поэтому команда
 * не объявлена в plugin.yml, а регистрируется через {@link CommandRegistrar}.
 */
public class RegionCommand extends Command {

    private final QweProtectStones plugin;

    /** Порядок важен: в таком же виде выводится справка. */
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    private final List<SubCommand> ordered = new ArrayList<>();

    public RegionCommand(QweProtectStones plugin, String name, List<String> aliases) {
        super(name);
        this.plugin = plugin;

        setAliases(aliases);
        setDescription("Управление приватами");
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
            sendHelp(sender, label);
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

    /** Команда без аргументов открывает меню привата, в котором стоит игрок. */
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

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(plugin.getLanguageManager().getMessage("help_header"));

        for (SubCommand sub : ordered) {
            if (sub.permission() != null && !sender.hasPermission(sub.permission())) continue;

            sender.sendMessage(plugin.getLanguageManager().getMessage("help_line",
                    "%command%", label,
                    "%sub%", sub.name(),
                    "%description%", plugin.getLanguageManager().getRawMessage(sub.helpKey())));
        }
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

        SubCommand sub = subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) return List.of();
        if (sub.permission() != null && !sender.hasPermission(sub.permission())) return List.of();

        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        return sub.complete(sender, player, rest);
    }
}
