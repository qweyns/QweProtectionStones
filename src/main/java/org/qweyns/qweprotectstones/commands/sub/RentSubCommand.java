package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.commands.SubCommand;
import org.qweyns.qweprotectstones.features.market.RegionRental;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.TrustLevel;
import org.qweyns.qweprotectstones.regions.event.RegionEvents;
import org.qweyns.qweprotectstones.regions.event.RegionMemberChangeEvent;

import java.util.List;
import java.util.Locale;

/**
 * Аренда приватов: {@code /ps rent offer <цена> <минуты>} публикует условия,
 * {@code /ps rent take} снимает (или продлевает) аренду,
 * {@code /ps rent cancel} убирает объявление.
 *
 * <p>Период, лимиты и уровень доступа арендатора — секция {@code market.rent}
 * в config.yml.</p>
 */
public class RentSubCommand extends AbstractRegionSubCommand implements SubCommand {

    public RentSubCommand(QweProtectStones plugin) {
        super(plugin);
    }

    @Override
    public String permission() {
        return QweProtectStones.PERMISSION_PREFIX + ".rent";
    }

    @Override
    public String name() { return "rent"; }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        if (!plugin.getConfigManager().getConfig().getBoolean("market.rent.enable", true)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("rent_disabled"));
            return;
        }

        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "offer" -> offer(player, args);
            case "take" -> take(player);
            case "cancel" -> cancel(player);
            default -> player.sendMessage(plugin.getLanguageManager().getMessage("rent_usage"));
        }
    }

    private void offer(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getLanguageManager().getMessage("rent_usage"));
            return;
        }

        Region region = regionWithTrust(player, TrustLevel.OWNER);
        if (region == null) return;

        double price;
        int minutes;
        try {
            price = Double.parseDouble(args[1].replace(',', '.'));
            minutes = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getLanguageManager().getMessage("rent_usage"));
            return;
        }

        double maxPrice = Math.max(1.0, plugin.getConfigManager().getConfig().getDouble("market.rent.max-price", 100_000.0));
        int minMinutes = Math.max(1, plugin.getConfigManager().getConfig().getInt("market.rent.min-duration-minutes", 10));
        int maxMinutes = Math.max(minMinutes, plugin.getConfigManager().getConfig().getInt("market.rent.max-duration-minutes", 4320));

        if (price <= 0 || price > maxPrice) {
            player.sendMessage(plugin.getLanguageManager().getMessage("rent_bad_price",
                    "%max%", SellSubCommand.money(maxPrice)));
            return;
        }
        if (minutes < minMinutes || minutes > maxMinutes) {
            player.sendMessage(plugin.getLanguageManager().getMessage("rent_bad_duration",
                    "%min%", String.valueOf(minMinutes), "%max%", String.valueOf(maxMinutes)));
            return;
        }

        plugin.getMarketManager().offerForRent(region, player, price, minutes);
        player.sendMessage(plugin.getLanguageManager().getMessage("rent_listed",
                "%id%", region.getShortId(),
                "%price%", SellSubCommand.money(price),
                "%minutes%", String.valueOf(minutes)));
    }

    private void take(Player player) {
        if (!plugin.getVaultHook().isEnabled()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("economy_required"));
            return;
        }

        Region region = regionUnderFeet(player);
        if (region == null) return;

        RegionRental rental = plugin.getMarketManager().getRental(region);
        if (rental == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("rent_not_available",
                    "%id%", region.getShortId()));
            return;
        }
        if (region.isOwner(player.getUniqueId()) || player.getUniqueId().equals(rental.ownerId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("rent_self"));
            return;
        }
        if (rental.isRented() && !player.getUniqueId().equals(rental.tenantId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("rent_occupied"));
            return;
        }
        if (!plugin.getVaultHook().hasMoney(player, rental.price())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("buy_no_money",
                    "%price%", SellSubCommand.money(rental.price())));
            return;
        }

        boolean extend = player.getUniqueId().equals(rental.tenantId());
        if (!plugin.getMarketManager().takeRent(player, region)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("buy_failed"));
            return;
        }

        player.sendMessage(plugin.getLanguageManager().getMessage(extend ? "rent_extended" : "rent_taken",
                "%id%", region.getShortId(),
                "%minutes%", String.valueOf(rental.durationMinutes()),
                "%price%", SellSubCommand.money(rental.price())));

        Player owner = plugin.getServer().getPlayer(rental.ownerId());
        if (owner != null) {
            owner.sendMessage(plugin.getLanguageManager().getMessage("rent_paid",
                    "%id%", region.getShortId(),
                    "%player%", player.getName(),
                    "%price%", SellSubCommand.money(rental.price())));
        }
    }

    private void cancel(Player player) {
        Region region;
        if (player.hasPermission("qweprotectstones.admin")) {
            // Админ может снять с аренды любой приват, где стоит.
            region = regionUnderFeet(player);
        } else {
            // Обычный игрок — только собственный.
            region = regionWithTrust(player, TrustLevel.OWNER);
        }
        if (region == null) return;

        RegionRental rental = plugin.getMarketManager().getRental(region);
        if (rental == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("rent_not_listed",
                    "%id%", region.getShortId()));
            return;
        }

        // Активную аренду завершаем сразу: доступ арендатора снимается.
        if (rental.tenantId() != null) {
            if (!RegionEvents.fireMemberChange(region, player, rental.tenantId(), rental.tenantName(),
                    RegionMemberChangeEvent.Action.UNTRUST, null)) {
                region.removeMember(rental.tenantId());
                plugin.getRegionStorage().save(region);
            }
            Player tenant = plugin.getServer().getPlayer(rental.tenantId());
            if (tenant != null) {
                tenant.sendMessage(plugin.getLanguageManager().getMessage("rent_expired",
                        "%id%", region.getShortId()));
            }
        }

        plugin.getMarketManager().cancelRental(region);
        player.sendMessage(plugin.getLanguageManager().getMessage("rent_cancelled",
                "%id%", region.getShortId()));
    }

    @Override
    public List<String> complete(CommandSender sender, Player player, String[] args) {
        if (args.length == 1) {
            return filter(List.of("offer", "take", "cancel"), args[0]);
        }
        // offer <цена> <минуты>: подсказываем потолок цены и границы
        // длительности периода — всё из market.rent (features.yml).
        if (args.length == 2 && args[0].equalsIgnoreCase("offer")) {
            return filter(List.of(String.valueOf(plugin.getConfigManager().getConfig()
                    .getInt("market.rent.max-price", 100000))), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("offer")) {
            var cfg = plugin.getConfigManager().getConfig();
            return filter(List.of(
                    String.valueOf(cfg.getInt("market.rent.min-duration-minutes", 10)),
                    String.valueOf(cfg.getInt("market.rent.max-duration-minutes", 4320))), args[2]);
        }
        return List.of();
    }
}
