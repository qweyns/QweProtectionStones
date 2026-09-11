package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.config.Tunables;
import org.qweyns.qweprotectstones.regions.Region;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class LogSubCommand extends AbstractRegionSubCommand {

    public LogSubCommand(QweProtectStones plugin) {
        super(plugin);
    }

    @Override
    public String permission() {
        return QweProtectStones.PERMISSION_PREFIX + ".log";
    }

    @Override
    public String name() {
        return "log";
    }

    @Override
    public List<String> aliases() {
        return List.of("history");
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {

        Region region = regionWithTrust(player, plugin.getProtectionService().requiredFor(Tunables.TrustAction.MANAGE));
        if (region == null) return;

        if (!plugin.getConfigManager().getConfig().getBoolean("settings.action_log.enable", true)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("log_disabled"));
            return;
        }

        int limit = plugin.getTunables().logPageSize();
        if (args.length > 0) {
            try {
                limit = Math.max(1, Math.min(plugin.getTunables().logMaxPageSize(), Integer.parseInt(args[0])));
            } catch (NumberFormatException ignored) {

            }
        }

        player.sendMessage(plugin.getLanguageManager().getMessage("log_header", "%region%", region.getShortId()));

        plugin.getRegionStorage().readLogAsync(region.getId(), limit, entries -> {
            if (!player.isOnline()) return;

            if (entries.isEmpty()) {
                player.sendMessage(plugin.getLanguageManager().getMessage("log_empty"));
                return;
            }

            SimpleDateFormat format = new SimpleDateFormat("dd.MM HH:mm");
            for (var entry : entries) {
                player.sendMessage(plugin.getLanguageManager().getMessage("log_line",
                        "%time%", format.format(new Date(entry.at())),
                        "%player%", entry.playerName() == null ? "?" : entry.playerName(),
                        "%action%", plugin.getLanguageManager().rawTemplate("log_action_" + entry.action()),
                        "%detail%", entry.detail() == null ? "" : entry.detail()));
            }
        });
    }

    @Override
    public List<String> complete(CommandSender sender, Player player, String[] args) {
        if (args.length != 1) return List.of();

        return filter(List.of(String.valueOf(plugin.getTunables().logPageSize()),
                String.valueOf(plugin.getTunables().logMaxPageSize())), args[0]);
    }
}
