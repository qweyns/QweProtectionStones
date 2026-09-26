package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    /** Ожидающее предложение: кому, какой приват, от кого и до какого времени. */
    private record Offer(UUID regionId, UUID fromId, String fromName, long expiresAt) { }
    private final Map<UUID, Offer> offers = new ConcurrentHashMap<>();

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        if (args.length > 0 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny"))) {
            answer(player, args[0].equalsIgnoreCase("accept"));
            return;
        }

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
        if (!checkLimit(player, target, region)) return;

        var cfg = plugin.getConfigManager().getConfig();
        if (!cfg.getBoolean("transfer.require-confirmation", true)) {
            complete(player, target, region);
            return;
        }

        int seconds = Math.max(10, cfg.getInt("transfer.confirmation-seconds", 60));
        offers.put(target.getUniqueId(), new Offer(region.getId(), player.getUniqueId(), player.getName(),
                System.currentTimeMillis() + seconds * 1000L));
        player.sendMessage(plugin.getLanguageManager().getMessage("transfer_offer_sent",
                "%player%", target.getName(), "%seconds%", String.valueOf(seconds)));
        plugin.getLanguageManager().sendList(target, "transfer_offer_received",
                "%player%", player.getName(), "%id%", region.getShortId(),
                "%type%", typeName(region), "%seconds%", String.valueOf(seconds),
                "%command%", plugin.getConfigManager().getCommandName());
    }

    private void answer(Player target, boolean accept) {
        Offer offer = offers.remove(target.getUniqueId());
        if (offer == null || offer.expiresAt() < System.currentTimeMillis()) {
            target.sendMessage(plugin.getLanguageManager().getMessage("transfer_offer_none"));
            return;
        }
        Player from = Bukkit.getPlayer(offer.fromId());
        Region region = plugin.getRegionManager().getById(offer.regionId());
        if (!accept) {
            target.sendMessage(plugin.getLanguageManager().getMessage("transfer_offer_denied"));
            if (from != null) from.sendMessage(plugin.getLanguageManager().getMessage("transfer_offer_denied_by",
                    "%player%", target.getName()));
            return;
        }
        // за время ожидания приват могли удалить, продать или передать другому
        boolean stillAllowed = region != null && from != null && (region.isOwner(from.getUniqueId())
                || from.hasPermission(plugin.getConfigManager().getAdminPermissionPrefix()));
        if (!stillAllowed || region.isOwner(target.getUniqueId())) {
            target.sendMessage(plugin.getLanguageManager().getMessage("transfer_offer_invalid"));
            return;
        }
        if (!checkLimit(target, target, region)) return;
        complete(from, target, region);
    }

    private boolean checkLimit(Player notify, Player target, Region region) {
        int limit = plugin.getConfigManager().getConfig().getBoolean("transfer.respect-limits", true)
                ? plugin.getRegionManager().limitReachedFor(target, region) : -1;
        if (limit < 0) return true;
        notify.sendMessage(plugin.getLanguageManager().getMessage("transfer_target_limit",
                "%player%", target.getName(), "%limit%", String.valueOf(limit)));
        return false;
    }

    private String typeName(Region region) {
        var type = plugin.getRegionTypes().byId(region.getTypeId());
        return type == null ? region.getTypeId() : type.displayName();
    }

    private void complete(Player player, Player target, Region region) {
        boolean hadSale = plugin.getMarketManager().getSale(region) != null;
        if (!plugin.getRegionManager().transferRegion(region, target.getUniqueId(), target.getName(), player)) return;
        if (hadSale) player.sendMessage(plugin.getLanguageManager().getMessage("transfer_sale_cancelled", "%id%", region.getShortId()));

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
        if (args.length != 1) return List.of();
        List<String> names = new java.util.ArrayList<>(onlinePlayerNames(args[0]));
        if (player != null && offers.containsKey(player.getUniqueId())) names.addAll(filter(List.of("accept", "deny"), args[0]));
        return names;
    }
}
