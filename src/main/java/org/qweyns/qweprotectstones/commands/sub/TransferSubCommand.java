package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;

import java.util.List;
import org.qweyns.qweprotectstones.regions.event.RegionEvents;

/** Передача привата другому игроку. Прежний владелец остаётся управляющим. */
public class TransferSubCommand extends AbstractRegionSubCommand {

    public TransferSubCommand(QweProtectStones plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "transfer";
    }

    @Override
    public List<String> aliases() {
        return List.of("give", "setowner");
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        Region region = regionUnderFeet(player);
        if (region == null) return;

        // Передать приват может только владелец — даже управляющему это не по силам.
        boolean allowed = region.isOwner(player.getUniqueId()) || player.hasPermission("qweprotectstones.admin");
        if (!allowed) {
            player.sendMessage(plugin.getLanguageManager().getMessage("not_an_owner"));
            return;
        }

        if (args.length == 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage("transfer_usage"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            // Требуем онлайн: так новый владелец точно узнает о передаче.
            player.sendMessage(plugin.getLanguageManager().getMessage("player_not_online", "%player%", args[0]));
            return;
        }
        if (target.getUniqueId().equals(region.getOwnerId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("transfer_already_owner"));
            return;
        }

        if (RegionEvents.fireTransfer(region, player, target.getUniqueId(), target.getName())) return;
        plugin.getRegionManager().transferRegion(region, target.getUniqueId(), target.getName());
        if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().update(region);
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().update(region);
        plugin.getHologramManager().createOrUpdateHologram(region);

        player.sendMessage(plugin.getLanguageManager().getMessage("transfer_done", "%player%", target.getName()));
        target.sendMessage(plugin.getLanguageManager().getMessage("transfer_received", "%player%", player.getName()));
    }

    @Override
    public List<String> complete(CommandSender sender, Player player, String[] args) {
        return args.length == 1 ? onlinePlayerNames(args[0]) : List.of();
    }
}
