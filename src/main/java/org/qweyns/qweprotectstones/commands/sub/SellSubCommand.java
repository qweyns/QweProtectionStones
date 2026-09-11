package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.commands.SubCommand;
import org.qweyns.qweprotectstones.features.market.RegionSale;
import org.qweyns.qweprotectstones.regions.Region;

import java.util.List;
import java.util.Locale;

/**
 * Рынок: {@code /ps sell <цена>} выставляет приват на продажу,
 * {@code /ps buy} покупает тот, в котором стоит игрок.
 *
 * <p>Лимиты и комиссия — в секции {@code market.sell} config.yml.</p>
 */
public class SellSubCommand extends AbstractRegionSubCommand implements SubCommand {

    public enum Mode {
        SELL("sell"),
        BUY("buy");

        private final String name;

        Mode(String name) { this.name = name; }

        public String key() { return name; }
    }

    private final Mode mode;

    public SellSubCommand(QweProtectStones plugin, Mode mode) {
        super(plugin);
        this.mode = mode;
    }

    @Override
    public String permission() {
        return QweProtectStones.PERMISSION_PREFIX + ".buysell";
    }

    @Override
    public String name() { return mode.key(); }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        if (!plugin.getConfigManager().getConfig().getBoolean("market.sell.enable", true)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("sell_disabled"));
            return;
        }

        if (mode == Mode.SELL) sell(player, args);
        else buy(player);
    }

    private void sell(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(plugin.getLanguageManager().getMessage("sell_usage"));
            return;
        }

        Region region = regionWithTrust(player, org.qweyns.qweprotectstones.regions.TrustLevel.OWNER);
        if (region == null) return;

        if (!allowDuringSiege() && plugin.isUnderSiege(region)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("sell_siege"));
            return;
        }

        String raw = args[0].toLowerCase(Locale.ROOT);
        if (raw.equals("off") || raw.equals("cancel") || raw.equals("снять") || raw.equals("0")) {
            if (plugin.getMarketManager().cancelSale(region)) {
                player.sendMessage(plugin.getLanguageManager().getMessage("sell_cancelled",
                        "%id%", region.getShortId()));
            } else {
                player.sendMessage(plugin.getLanguageManager().getMessage("sell_not_listed",
                        "%id%", region.getShortId()));
            }
            return;
        }

        double price;
        try {
            price = Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getLanguageManager().getMessage("sell_bad_price", "%value%", args[0]));
            return;
        }

        double max = maxPrice();
        if (price <= 0 || price > max) {
            player.sendMessage(plugin.getLanguageManager().getMessage("sell_too_much", "%max%", money(max)));
            return;
        }

        plugin.getMarketManager().listForSale(region, player, price);
        player.sendMessage(plugin.getLanguageManager().getMessage("sell_listed",
                "%id%", region.getShortId(), "%price%", money(price)));
    }

    private void buy(Player player) {
        Region region = regionUnderFeet(player);
        if (region == null) return;

        RegionSale sale = plugin.getMarketManager().getSale(region);
        if (sale == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("buy_not_listed",
                    "%id%", region.getShortId()));
            return;
        }
        if (region.isOwner(player.getUniqueId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("buy_self"));
            return;
        }
        if (!allowDuringSiege() && plugin.isUnderSiege(region)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("buy_siege"));
            return;
        }
        if (!plugin.getVaultHook().isEnabled()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("economy_required"));
            return;
        }
        if (!plugin.getVaultHook().hasMoney(player, sale.price())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("buy_no_money",
                    "%price%", money(sale.price())));
            return;
        }

        if (!plugin.getMarketManager().buy(player, region)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("buy_failed"));
            return;
        }

        if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().update(region);
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().update(region);

        player.sendMessage(plugin.getLanguageManager().getMessage("buy_success",
                "%id%", region.getShortId(), "%price%", money(sale.price())));

        Player seller = plugin.getServer().getPlayer(sale.sellerId());
        if (seller != null) {
            seller.sendMessage(plugin.getLanguageManager().getMessage("buy_sold",
                    "%id%", region.getShortId(),
                    "%player%", player.getName(),
                    "%price%", money(sale.price())));
        }
    }

    private boolean allowDuringSiege() {
        return plugin.getConfigManager().getConfig().getBoolean("market.sell.allow-during-siege", false);
    }

    private double maxPrice() {
        return Math.max(1.0, plugin.getConfigManager().getConfig().getDouble("market.sell.max-price", 1_000_000.0));
    }

    /** Красивое число: без хвоста «.0» у целых цен. */
    static String money(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) return String.valueOf((long) value);
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public List<String> complete(CommandSender sender, Player player, String[] args) {
        if (mode == Mode.SELL && args.length == 1) {
            // off — снять с продажи; число — потолок цены из market.sell.
            return filter(List.of("off", String.valueOf(plugin.getConfigManager().getConfig()
                    .getInt("market.sell.max-price", 1000000))), args[0]);
        }
        return List.of();
    }
}
