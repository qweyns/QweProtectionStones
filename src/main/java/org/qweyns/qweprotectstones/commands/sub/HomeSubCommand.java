package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;

import java.util.ArrayList;
import java.util.List;

public class HomeSubCommand extends AbstractRegionSubCommand {

    public HomeSubCommand(QweProtectStones plugin) {
        super(plugin);
    }

    @Override
    public String permission() {
        return QweProtectStones.PERMISSION_PREFIX + ".home";
    }

    @Override
    public String name() {
        return "home";
    }

    @Override
    public List<String> aliases() {
        return List.of("tp", "go");
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        List<Region> regions = plugin.getRegionManager().getAccessibleRegions(player.getUniqueId());
        if (regions.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("list_empty"));
            return;
        }

        Region target;
        if (args.length == 0) {
            if (regions.size() > 1) {
                player.sendMessage(plugin.getLanguageManager().getMessage("home_specify"));
                return;
            }
            target = regions.get(0);
        } else {
            target = plugin.getRegionManager().getByShortId(args[0]);
            if (target == null || !regions.contains(target)) {
                player.sendMessage(plugin.getLanguageManager().getMessage("region_not_found", "%id%", args[0]));
                return;
            }
        }

        Location home = target.getHomeLocation();
        if (home == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("region_world_not_loaded",
                    "%world%", target.getWorldName()));
            return;
        }

        plugin.getHomeWarmup().teleport(player, target, home);
    }

    @Override
    public List<String> complete(CommandSender sender, Player player, String[] args) {
        if (args.length != 1 || player == null) return List.of();

        List<String> ids = new ArrayList<>();
        for (Region region : plugin.getRegionManager().getAccessibleRegions(player.getUniqueId())) {
            ids.add(region.getShortId());
        }
        return filter(ids, args[0]);
    }
}
