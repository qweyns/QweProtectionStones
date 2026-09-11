package org.qweyns.qweprotectstones.features.market;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.TrustLevel;
import org.qweyns.qweprotectstones.regions.event.RegionEvents;
import org.qweyns.qweprotectstones.regions.event.RegionMemberChangeEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Рынок приватов: продажа ({@code /ps sell}, {@code /ps buy}) и аренда
 * ({@code /ps rent}, {@code /ps rent take}).
 *
 * <p>Все цены, лимиты и уровни доступа задаются в секции {@code market}
 * config.yml. Денежные операции — через Vault; без экономики рынок просто
 * отказывается работать и сообщает об этом.</p>
 */
public class MarketManager {

    private final QweProtectStones plugin;

    private final Map<UUID, RegionSale> sales = new ConcurrentHashMap<>();
    private final Map<UUID, RegionRental> rentals = new ConcurrentHashMap<>();

    public MarketManager(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    /** Загрузка объявлений из базы: вызывается один раз при старте. */
    public void load() {
        sales.putAll(plugin.getRegionStorage().loadSales());
        rentals.putAll(plugin.getRegionStorage().loadRentals());
        if (!sales.isEmpty()) plugin.getLogger().info("Приватов на продаже: " + sales.size());
        if (!rentals.isEmpty()) plugin.getLogger().info("Приватов в аренде: " + rentals.size());
    }

    /** Периодическое снятие истёкших аренд. */
    public void startExpiryTask() {
        long intervalMinutes = Math.max(1, plugin.getConfigManager().getConfig()
                .getInt("market.rent.check-interval-minutes", 5));
        long intervalTicks = TimeUnit.MINUTES.toSeconds(intervalMinutes) * 20L;
        // Первый прогон с задержкой: сервер ещё догружается.
        plugin.getSchedulers().runTimer(this::expireRentals, intervalTicks, intervalTicks);
    }

    // ------------------------------------------------------------------
    // Продажа
    // ------------------------------------------------------------------

    public RegionSale getSale(Region region) {
        return region == null ? null : sales.get(region.getId());
    }

    /** Выставить/перевоценить. Проверки прав и аргументов — в команде. */
    public void listForSale(Region region, Player seller, double price) {
        RegionSale sale = new RegionSale(region.getId(), seller.getUniqueId(), seller.getName(),
                price, System.currentTimeMillis());
        sales.put(region.getId(), sale);
        plugin.getRegionStorage().saveSaleNow(sale);
    }

    /** Снять с продажи (цена 0 или off). */
    public boolean cancelSale(Region region) {
        if (region == null || sales.remove(region.getId()) == null) return false;
        plugin.getRegionStorage().deleteSaleNow(region.getId());
        return true;
    }

    /**
     * Покупка привата. Все проверки (наличие объявления, денег, осада) уже
     * пройдены командой; здесь только транзакция и смена владельца.
     *
     * @return true при успехе
     */
    public boolean buy(Player buyer, Region region) {
        RegionSale sale = sales.get(region.getId());
        if (sale == null) return false;

        if (RegionEvents.fireTransfer(region, buyer, buyer.getUniqueId(), buyer.getName())) return false;
        if (!plugin.getVaultHook().takeMoney(buyer, sale.price())) return false;

        // Комиссия уходит «в никуда» (экономика сервера распоряжается ими сама).
        double tax = Math.max(0.0, Math.min(100.0,
                plugin.getConfigManager().getConfig().getDouble("market.sell.tax-percent", 0.0)));
        double payout = sale.price() * (100.0 - tax) / 100.0;

        OfflinePlayer seller = Bukkit.getOfflinePlayer(sale.sellerId());
        if (!plugin.getVaultHook().giveMoney(seller, payout)) {
            // Зачислить не вышло (нет плагина экономики) — возвращаем деньги.
            plugin.getVaultHook().giveMoney(buyer, sale.price());
            plugin.getLogger().warning("Покупка привата " + region.getShortId()
                    + ": не удалось зачислить " + payout + " продавцу. Покупателю возвращены деньги.");
            return false;
        }

        UUID previousOwner = region.getOwnerId();
        plugin.getRegionManager().transferRegion(region, buyer.getUniqueId(), buyer.getName());
        if (previousOwner != null) region.removeMember(previousOwner);
        plugin.getRegionStorage().save(region);

        cancelSale(region);
        return true;
    }

    // ------------------------------------------------------------------
    // Аренда
    // ------------------------------------------------------------------

    public RegionRental getRental(Region region) {
        return region == null ? null : rentals.get(region.getId());
    }

    /** Опубликовать условия аренды. Проверки — в команде. */
    public void offerForRent(Region region, Player owner, double price, int durationMinutes) {
        RegionRental rental = new RegionRental(region.getId(), owner.getUniqueId(), owner.getName(),
                price, durationMinutes, null, null, 0L);
        rentals.put(region.getId(), rental);
        plugin.getRegionStorage().saveRentalNow(rental);
    }

    /** Снять приват с аренды (владельцем или при удалении привата). */
    public boolean cancelRental(Region region) {
        if (region == null || rentals.remove(region.getId()) == null) return false;
        plugin.getRegionStorage().deleteRentalNow(region.getId());
        return true;
    }

    /**
     * Снять приват в аренду (или продлить свою аренду). Транзакция и выдача
     * доступа на настраиваемый период.
     *
     * @return true при успехе
     */
    public boolean takeRent(Player tenant, Region region) {
        RegionRental rental = rentals.get(region.getId());
        if (rental == null) return false;

        boolean extend = tenant.getUniqueId().equals(rental.tenantId());
        if (rental.isRented() && !extend) return false; // занято другим игроком

        if (!plugin.getVaultHook().takeMoney(tenant, rental.price())) return false;

        OfflinePlayer owner = Bukkit.getOfflinePlayer(rental.ownerId());
        plugin.getVaultHook().giveMoney(owner, rental.price());

        TrustLevel level = rentTrustLevel();

        long until;
        if (extend) {
            // Продление: время добавляется к текущему сроку.
            until = Math.max(System.currentTimeMillis(), rental.rentedUntil())
                    + TimeUnit.MINUTES.toMillis(rental.durationMinutes());
        } else {
            until = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(rental.durationMinutes());
            if (RegionEvents.fireMemberChange(region, null, tenant.getUniqueId(), tenant.getName(),
                    RegionMemberChangeEvent.Action.TRUST, level)) {
                // Событие отменили — возвращаем деньги и не выдаём доступ.
                plugin.getVaultHook().giveMoney(tenant, rental.price());
                return false;
            }
            region.setMember(tenant.getUniqueId(), tenant.getName(), level);
        }

        RegionRental updated = new RegionRental(rental.regionId(), rental.ownerId(), rental.ownerName(),
                rental.price(), rental.durationMinutes(), tenant.getUniqueId(), tenant.getName(), until);
        rentals.put(region.getId(), updated);
        plugin.getRegionStorage().saveRentalNow(updated);
        plugin.getRegionStorage().save(region);
        return true;
    }

    /** Уровень доступа арендатора из конфига. */
    public TrustLevel rentTrustLevel() {
        String raw = plugin.getConfigManager().getConfig().getString("market.rent.trust-level", "manager");
        Optional<TrustLevel> parsed = TrustLevel.parse(raw);
        return parsed.orElse(TrustLevel.MANAGER);
    }

    /**
     * Один проход: снимаем истёкшие аренды. Вызывается таймером и при
     * удалении привата. Уведомляем арендатора, если он онлайн.
     */
    public void expireRentals() {
        long now = System.currentTimeMillis();
        for (RegionRental rental : rentals.values()) {
            if (rental.tenantId() == null || rental.rentedUntil() > now) continue;

            Region region = plugin.getRegionManager().getById(rental.regionId());
            if (region != null) {
                if (RegionEvents.fireMemberChange(region, null, rental.tenantId(), rental.tenantName(),
                        RegionMemberChangeEvent.Action.UNTRUST, null)) {
                    continue; // другой плагин продлил аренду своим событием
                }
                region.removeMember(rental.tenantId());
                plugin.getRegionStorage().save(region);
            }

            RegionRental freed = new RegionRental(rental.regionId(), rental.ownerId(), rental.ownerName(),
                    rental.price(), rental.durationMinutes(), null, null, 0L);
            rentals.put(rental.regionId(), freed);
            plugin.getRegionStorage().saveRentalNow(freed);

            Player tenant = Bukkit.getPlayer(rental.tenantId());
            if (tenant != null) {
                tenant.sendMessage(plugin.getLanguageManager().getMessage("rent_expired",
                        "%id%", region != null ? region.getShortId() : rental.regionId().toString().substring(0, 8)));
            }
        }
    }
}
