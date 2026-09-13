package org.qweyns.qweprotectstones.features.market;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.TrustLevel;
import org.qweyns.qweprotectstones.regions.event.RegionEvents;
import org.qweyns.qweprotectstones.regions.event.RegionMemberChangeEvent;
import org.qweyns.qweprotectstones.scheduler.Schedulers;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MarketManager {

    private final QweProtectStones plugin;

    private final Map<UUID, RegionSale> sales = new ConcurrentHashMap<>();
    private final Map<UUID, RegionRental> rentals = new ConcurrentHashMap<>();

    private Schedulers.Task expiryTask;

    public int salesCount() {
        return sales.size();
    }

    public int rentalListingsCount() {
        return rentals.size();
    }

    public int rentedCount() {
        return (int) rentals.values().stream().filter(RegionRental::isRented).count();
    }

    public MarketManager(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public void load() {
        sales.putAll(plugin.getRegionStorage().loadSales());
        rentals.putAll(plugin.getRegionStorage().loadRentals());
        if (!sales.isEmpty()) plugin.getLogger().info("Приватов на продаже: " + sales.size());
        if (!rentals.isEmpty()) plugin.getLogger().info("Приватов в аренде: " + rentals.size());
    }

    public void startExpiryTask() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
        long intervalMinutes = Math.max(1, plugin.getConfigManager().getConfig()
                .getInt("market.rent.check-interval-minutes", 5));
        long intervalTicks = TimeUnit.MINUTES.toSeconds(intervalMinutes) * 20L;

        expiryTask = plugin.getSchedulers().runTimer(this::expireRentals, intervalTicks, intervalTicks);
    }

    public RegionSale getSale(Region region) {
        return region == null ? null : sales.get(region.getId());
    }

    public void listForSale(Region region, Player seller, double price) {
        RegionSale sale = new RegionSale(region.getId(), seller.getUniqueId(), seller.getName(),
                price, System.currentTimeMillis());
        synchronized (sales) {
            sales.put(region.getId(), sale);
        }
        plugin.getRegionStorage().saveSale(sale);
    }

    public boolean cancelSale(Region region) {
        if (region == null) return false;
        boolean removed;
        synchronized (sales) {
            removed = sales.remove(region.getId()) != null;
        }
        if (removed) plugin.getRegionStorage().deleteSale(region.getId());
        return removed;
    }

    /** Смена владельца: продажа снимается, аренда перепривязывается к новому. Возвращает true, если продажа была активна. */
    public boolean handleOwnershipChange(Region region, UUID newOwnerId, String newOwnerName) {
        boolean hadSale = cancelSale(region);
        rebindRentalOwner(region, newOwnerId, newOwnerName);
        return hadSale;
    }

    private void rebindRentalOwner(Region region, UUID newOwnerId, String newOwnerName) {
        synchronized (rentals) {
        rentals.computeIfPresent(region.getId(), (id, rental) -> {
            RegionRental rebound = new RegionRental(rental.regionId(), newOwnerId, newOwnerName,
                    rental.price(), rental.durationMinutes(), rental.tenantId(), rental.tenantName(), rental.rentedUntil());
            plugin.getRegionStorage().saveRental(rebound);
            return rebound;
        });
        }
    }

    public boolean buy(Player buyer, Region region) {
        // на Folia покупатели могут прийти из разных потоков — серия атомарна
        synchronized (sales) {
            return buyLocked(buyer, region);
        }
    }

    private boolean buyLocked(Player buyer, Region region) {
        // платим только за живой приват: удалённый из менеджера мог остаться в руках команды
        if (plugin.getRegionManager().getById(region.getId()) != region) return false;

        RegionSale sale = sales.get(region.getId());
        if (sale == null) return false;

        if (RegionEvents.fireTransfer(region, buyer, buyer.getUniqueId(), buyer.getName())) return false;
        if (!plugin.getVaultHook().takeMoney(buyer, sale.price())) return false;

        // покупка гонится с удалением привата: не доехала — деньги назад
        if (plugin.getRegionManager().getById(region.getId()) != region) {
            plugin.getVaultHook().giveMoney(buyer, sale.price());
            return false;
        }

        // комиссия просто сгорает
        double tax = Math.max(0.0, Math.min(100.0,
                plugin.getConfigManager().getConfig().getDouble("market.sell.tax-percent", 0.0)));
        double payout = sale.price() * (100.0 - tax) / 100.0;

        OfflinePlayer seller = Bukkit.getOfflinePlayer(sale.sellerId());
        if (!plugin.getVaultHook().giveMoney(seller, payout)) {

            plugin.getVaultHook().giveMoney(buyer, sale.price());
            plugin.getLogger().warning("Покупка привата " + region.getShortId()
                    + ": не удалось зачислить " + payout + " продавцу. Покупателю возвращены деньги.");
            return false;
        }

        UUID previousOwner = region.getOwnerId();
        plugin.getRegionManager().transferRegion(region, buyer.getUniqueId(), buyer.getName());
        if (previousOwner != null) region.removeMember(previousOwner);
        plugin.getRegionStorage().save(region);

        // объявление уходит атомарно вместе с деньгами, аренда — за новым владельцем
        sales.remove(region.getId());
        plugin.getRegionStorage().deleteSale(region.getId());
        rebindRentalOwner(region, buyer.getUniqueId(), buyer.getName());
        return true;
    }

    public RegionRental getRental(Region region) {
        return region == null ? null : rentals.get(region.getId());
    }

    public void offerForRent(Region region, Player owner, double price, int durationMinutes) {
        RegionRental rental = new RegionRental(region.getId(), owner.getUniqueId(), owner.getName(),
                price, durationMinutes, null, null, 0L);
        synchronized (rentals) {
            rentals.put(region.getId(), rental);
        }
        plugin.getRegionStorage().saveRental(rental);
    }

    public boolean cancelRental(Region region) {
        if (region == null) return false;
        boolean removed;
        synchronized (rentals) {
            removed = rentals.remove(region.getId()) != null;
        }
        if (removed) plugin.getRegionStorage().deleteRental(region.getId());
        return removed;
    }

    public boolean takeRent(Player tenant, Region region) {
        synchronized (rentals) {
            return takeRentLocked(tenant, region);
        }
    }

    private boolean takeRentLocked(Player tenant, Region region) {
        RegionRental rental = rentals.get(region.getId());
        if (rental == null) return false;

        // забаненный не снимает и не продлевает
        if (region.isBanned(tenant.getUniqueId())) return false;

        // продление только действующему участнику, изгнанный платит как новый

        boolean extend = tenant.getUniqueId().equals(rental.tenantId())
                && region.getMember(tenant.getUniqueId()).isPresent();
        if (rental.isRented() && !extend) return false;

        TrustLevel level = rentTrustLevel();

        // вето чужих плагинов слушаем до оплаты: после неё пришлось бы забирать
        // деньги уже у получившего их владельца
        if (!extend && RegionEvents.fireMemberChange(region, null, tenant.getUniqueId(), tenant.getName(),
                RegionMemberChangeEvent.Action.TRUST, level)) {
            return false;
        }

        if (!plugin.getVaultHook().takeMoney(tenant, rental.price())) return false;

        // аренда гонится с удалением привата: не доехала — деньги назад
        if (plugin.getRegionManager().getById(region.getId()) != region) {
            plugin.getVaultHook().giveMoney(tenant, rental.price());
            return false;
        }

        OfflinePlayer owner = Bukkit.getOfflinePlayer(rental.ownerId());
        if (!plugin.getVaultHook().giveMoney(owner, rental.price())) {
            plugin.getVaultHook().giveMoney(tenant, rental.price());
            plugin.getLogger().warning("Аренда привата " + region.getShortId()
                    + ": не удалось зачислить " + rental.price() + " владельцу. Арендатору возвращены деньги.");
            return false;
        }

        long until;
        if (extend) {

            until = Math.max(System.currentTimeMillis(), rental.rentedUntil())
                    + TimeUnit.MINUTES.toMillis(rental.durationMinutes());
        } else {
            until = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(rental.durationMinutes());
            region.setMember(tenant.getUniqueId(), tenant.getName(), level);
        }

        RegionRental updated = new RegionRental(rental.regionId(), rental.ownerId(), rental.ownerName(),
                rental.price(), rental.durationMinutes(), tenant.getUniqueId(), tenant.getName(), until);
        rentals.put(region.getId(), updated);
        plugin.getRegionStorage().saveRental(updated);
        plugin.getRegionStorage().save(region);
        return true;
    }

    public TrustLevel rentTrustLevel() {
        String raw = plugin.getConfigManager().getConfig().getString("market.rent.trust-level", "container");
        Optional<TrustLevel> parsed = TrustLevel.parse(raw);
        return parsed.orElse(TrustLevel.CONTAINER);
    }

    public void expireRentals() {
        synchronized (rentals) {
            expireRentalsLocked(System.currentTimeMillis());
        }
    }

    private void expireRentalsLocked(long now) {
        for (RegionRental rental : rentals.values()) {
            if (rental.tenantId() == null || rental.rentedUntil() > now) continue;

            Region region = plugin.getRegionManager().getById(rental.regionId());
            if (region != null) {
                if (RegionEvents.fireMemberChange(region, null, rental.tenantId(), rental.tenantName(),
                        RegionMemberChangeEvent.Action.UNTRUST, null)) {
                    continue;
                }
                region.removeMember(rental.tenantId());
                plugin.getRegionStorage().save(region);
            }

            RegionRental freed = new RegionRental(rental.regionId(), rental.ownerId(), rental.ownerName(),
                    rental.price(), rental.durationMinutes(), null, null, 0L);
            rentals.put(rental.regionId(), freed);
            plugin.getRegionStorage().saveRental(freed);

            Player tenant = Bukkit.getPlayer(rental.tenantId());
            if (tenant != null) {
                // таймер тикает в глобальном потоке — сообщение через планировщик игрока
                plugin.getSchedulers().runAtEntity(tenant, () -> tenant.sendMessage(
                        plugin.getLanguageManager().getMessage("rent_expired",
                                "%id%", region != null ? region.getShortId() : rental.regionId().toString().substring(0, 8))));
            }
        }
    }
}
