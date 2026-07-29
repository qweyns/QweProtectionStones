package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.TrustLevel;
import org.qweyns.qweprotectstones.commands.SubCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Общие помощники для подкоманд: поиск привата под ногами и проверка прав. */
abstract class AbstractRegionSubCommand implements SubCommand {

    protected final QweProtectStones plugin;

    protected AbstractRegionSubCommand(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    /** Приват, в котором стоит игрок. Сам сообщает об ошибке и возвращает null. */
    protected Region regionUnderFeet(Player player) {
        Region region = plugin.getRegionManager().getRegionAt(player.getLocation());
        if (region == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("not_in_region"));
            return null;
        }
        return region;
    }

    /** Приват с проверкой уровня доступа. Сам сообщает об ошибке и возвращает null. */
    protected Region regionWithTrust(Player player, TrustLevel required) {
        Region region = regionUnderFeet(player);
        if (region == null) return null;

        if (!plugin.getProtectionService().has(region, player, required)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("no_region_access",
                    "%level%", plugin.getLanguageManager().getRawMessage("trust_" + required.key())));
            return null;
        }
        return region;
    }

    protected List<String> filter(List<String> candidates, String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();

        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lowered)) result.add(candidate);
        }
        return result;
    }

    /** Ники онлайн-игроков для автодополнения. */
    protected List<String> onlinePlayerNames(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            names.add(online.getName());
        }
        return filter(names, prefix);
    }
}
