package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.qweyns.qweprotectstones.regions.event.RegionEvents;

/** Просмотр и изменение флагов привата. */
public class FlagSubCommand extends AbstractRegionSubCommand {

    public FlagSubCommand(QweProtectStones plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "flag";
    }

    @Override
    public List<String> aliases() {
        return List.of("flags", "set");
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        Region region = regionWithTrust(player, plugin.getTunables().flagEditLevel());
        if (region == null) return;

        if (args.length == 0) {
            showAll(player, region);
            return;
        }

        var parsed = RegionFlag.parse(args[0]);
        if (parsed.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("flag_unknown", "%flag%", args[0]));
            return;
        }

        RegionFlag flag = parsed.get();
        boolean admin = player.hasPermission("qweprotectstones.admin");
        if (!isEditable(flag, admin)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("flag_not_editable", "%flag%", flag.key()));
            return;
        }

        if (args.length == 1) {
            player.sendMessage(plugin.getLanguageManager().getMessage("flag_current",
                    "%flag%", flag.key(),
                    "%value%", localizedValue(plugin.getProtectionService().flag(region, flag))));
            return;
        }

        applyValue(player, region, flag, args[1]);
    }

    private void applyValue(Player player, Region region, RegionFlag flag, String rawValue) {
        String value = rawValue.toLowerCase(Locale.ROOT);

        if (value.equals("reset") || value.equals("default") || value.equals("сброс")) {
            if (RegionEvents.fireFlagChange(region, player, flag, null)) return;
            region.resetFlag(flag);
            plugin.getRegionStorage().save(region);
            player.sendMessage(plugin.getLanguageManager().getMessage("flag_reset",
                    "%flag%", flag.key(),
                    "%value%", localizedValue(plugin.getProtectionService().flag(region, flag))));
            return;
        }

        Boolean parsed = parseBoolean(value);
        if (parsed == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("flag_bad_value", "%value%", rawValue));
            return;
        }

        region.setFlag(flag, parsed);
        plugin.getRegionStorage().save(region);
        player.sendMessage(plugin.getLanguageManager().getMessage("flag_set",
                "%flag%", flag.key(), "%value%", localizedValue(parsed)));
    }

    /**
     * Флаг доступен игроку, если он вообще предназначен для игроков и не закрыт
     * администрацией через {@code flags.locked}. Админ обходит оба ограничения.
     */
    private boolean isEditable(RegionFlag flag, boolean admin) {
        if (admin) return true;
        return flag.playerEditable() && !plugin.getTunables().isFlagLocked(flag);
    }

    private Boolean parseBoolean(String value) {
        return switch (value) {
            case "true", "on", "yes", "allow", "да", "вкл" -> Boolean.TRUE;
            case "false", "off", "no", "deny", "нет", "выкл" -> Boolean.FALSE;
            default -> null;
        };
    }

    private void showAll(Player player, Region region) {
        boolean admin = player.hasPermission("qweprotectstones.admin");

        player.sendMessage(plugin.getLanguageManager().getMessage("flag_header"));
        for (RegionFlag flag : RegionFlag.values()) {
            if (!isEditable(flag, admin)) continue;

            boolean overridden = region.getFlagOverride(flag).isPresent();
            player.sendMessage(plugin.getLanguageManager().getMessage(
                    overridden ? "flag_line_custom" : "flag_line",
                    "%flag%", flag.key(),
                    "%value%", localizedValue(plugin.getProtectionService().flag(region, flag))));
        }
    }

    private String localizedValue(boolean value) {
        return plugin.getLanguageManager().getRawMessage(value ? "flag_value_on" : "flag_value_off");
    }

    @Override
    public List<String> complete(CommandSender sender, Player player, String[] args) {
        boolean admin = sender.hasPermission("qweprotectstones.admin");

        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (RegionFlag flag : RegionFlag.values()) {
                if (isEditable(flag, admin)) names.add(flag.key());
            }
            return filter(names, args[0]);
        }
        if (args.length == 2) return filter(List.of("true", "false", "reset"), args[1]);
        return List.of();
    }
}
