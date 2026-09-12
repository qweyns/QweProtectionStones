package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;

import java.util.List;
import org.qweyns.qweprotectstones.regions.event.RegionEvents;

public class TransferSubCommand extends AbstractRegionSubCommand {

    public TransferSubCommand(QweProtectStones plugin) {
        super(plugin);
    }

    @Override
    public String permission() {
        return QweProtectStones.PERMISSION_PREFIX + ".transfer";
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

        boolean allowed = region.isOwner(player.getUniqueId())
                || player.hasPermission(plugin.getConfigManager().getAdminPermissionPrefix());
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
            // требуем онлайн, новый владелец должен увидеть передачу
            player.sendMessage(plugin.getLanguageManager().getMessage("player_not_online", "%player%", args[0]));
            return;
        }
        if (target.getUniqueId().equals(region.getOwnerId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("transfer_already_owner"));
            return;
        }

        if (RegionEvents.fireTransfer(region, player, target.getUniqueId(), target.getName())) return;
        plugin.getRegionManager().transferRegion(region, target.getUniqueId(), target.getName());

        if (plugin.getMarketManager().handleOwnershipChange(region, target.getUniqueId(), target.getName())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("transfer_sale_cancelled",
                    "%id%", region.getShortId()));
        }

        if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().update(region);
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().update(region);
        plugin.getHologramManager().createOrUpdateHologram(region);

        plugin.getCriticalFileLogger().log("PLAYER_TRANSFER",
                "by=" + player.getName() + " region=" + region.getShortId()
                        + " owner=" + region.getOwnerName() + " to=" + target.getName());
        player.sendMessage(plugin.getLanguageManager().getMessage("transfer_done", "%player%", target.getName()));
        target.sendMessage(plugin.getLanguageManager().getMessage("transfer_received", "%player%", player.getName()));
    }

    @Override
    public List<String> complete(CommandSender sender, Player player, String[] args) {
        return args.length == 1 ? onlinePlayerNames(args[0]) : List.of();
    }
}
