package org.qweyns.qweprotectstones.features.market;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.TrustLevel;
import org.qweyns.qweprotectstones.regions.event.RegionEvents;
import org.qweyns.qweprotectstones.regions.event.RegionMemberChangeEvent;
import org.qweyns.qweprotectstones.scheduler.Schedulers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Сделка резервирует регион без ожидания и без мониторов вокруг сторонних событий/Vault. */
public class MarketManager {
    private final QweProtectStones plugin;
    private final Map<UUID, RegionSale> sales = new ConcurrentHashMap<>();
    private final Map<UUID, RegionRental> rentals = new ConcurrentHashMap<>();
    private Schedulers.Task expiryTask;

    public MarketManager(QweProtectStones plugin) { this.plugin = plugin; }
    public int salesCount() { return sales.size(); }
    public int rentalListingsCount() { return rentals.size(); }
    public int rentedCount() { return (int) rentals.values().stream().filter(RegionRental::isRented).count(); }
    private boolean live(Region region) { return plugin.getRegionManager().getById(region.getId()) == region; }
    private static boolean priceValid(double price) { return Double.isFinite(price) && price > 0; }

    public void load() {
        sales.putAll(plugin.getRegionStorage().loadSales());
        rentals.putAll(plugin.getRegionStorage().loadRentals());
        // Устаревшая сделка не даёт права получать деньги прежнему владельцу.
        sales.values().removeIf(s -> {
            Region r = plugin.getRegionManager().getById(s.regionId());
            boolean stale = r == null || !r.isOwner(s.sellerId()) || !priceValid(s.price());
            if (stale) plugin.getRegionStorage().deleteSale(s.regionId());
            return stale;
        });
        rentals.values().removeIf(r -> {
            Region region = plugin.getRegionManager().getById(r.regionId());
            boolean stale = region == null || !priceValid(r.price()) || r.durationMinutes() <= 0;
            if (stale) plugin.getRegionStorage().deleteRental(r.regionId());
            return stale;
        });
        for (RegionRental rental : rentals.values()) {
            Region region = plugin.getRegionManager().getById(rental.regionId());
            if (!region.isOwner(rental.ownerId())) rebindRentalOwner(region, region.getOwnerId(), region.getOwnerName());
        }
    }

    public void startExpiryTask() {
        if (expiryTask != null) expiryTask.cancel();
        long ticks = TimeUnit.MINUTES.toSeconds(Math.max(1, plugin.getConfigManager().getConfig()
                .getInt("market.rent.check-interval-minutes", 5))) * 20L;
        expiryTask = plugin.getSchedulers().runTimer(this::expireRentals, ticks, ticks);
    }

    public RegionSale getSale(Region region) { return region == null ? null : sales.get(region.getId()); }
    public RegionRental getRental(Region region) { return region == null ? null : rentals.get(region.getId()); }

    public boolean listForSale(Region region, Player seller, double price) {
        if (region == null || !priceValid(price)) return false;
        try (Region.Operation op = region.tryOperation()) {
            if (op == null || !live(region) || !region.isOwner(seller.getUniqueId())) return false;
            RegionSale sale = new RegionSale(region.getId(), seller.getUniqueId(), seller.getName(), price, System.currentTimeMillis());
            sales.put(region.getId(), sale);
            plugin.getRegionStorage().saveSale(sale);
            return true;
        }
    }

    private boolean removeSale(Region region) {
        boolean removed = sales.remove(region.getId()) != null;
        if (removed) plugin.getRegionStorage().deleteSale(region.getId());
        return removed;
    }

    public boolean cancelSale(Region region) {
        if (region == null) return false;
        try (Region.Operation op = region.tryOperation()) { return op != null && removeSale(region); }
    }

    /** Совместимость: основной путь передачи теперь находится в RegionManager. */
    public boolean handleOwnershipChange(Region region, UUID owner, String name) {
        try (Region.Operation op = region.tryOperation()) {
            if (op == null || !region.isOwner(owner)) return false;
            boolean hadSale = getSale(region) != null;
            ownershipWithin(region, owner, name, op);
            return hadSale;
        }
    }

    public void ownershipWithin(Region region, UUID owner, String name, Region.Operation op) {
        if (!op.owns(region)) throw new IllegalStateException("Нет резервации региона");
        removeSale(region);
        rebindRentalOwner(region, owner, name);
    }

    private void rebindRentalOwner(Region region, UUID owner, String name) {
        RegionRental rental = rentals.get(region.getId());
        if (rental == null) return;
        RegionRental rebound = new RegionRental(region.getId(), owner, name, rental.price(), rental.durationMinutes(),
                rental.tenantId(), rental.tenantName(), rental.rentedUntil());
        rentals.put(region.getId(), rebound);
        plugin.getRegionStorage().saveRental(rebound);
    }

    /** Удаление привата снимает сделки, не создавая ещё одно отменяемое событие. */
    public void clearWithin(Region region, Region.Operation op) {
        if (!op.owns(region)) throw new IllegalStateException("Нет резервации региона");
        removeSale(region);
        if (rentals.remove(region.getId()) != null) plugin.getRegionStorage().deleteRental(region.getId());
    }

    private void refund(Player player, double amount, Region region) {
        if (!plugin.getVaultHook().giveMoney(player, amount)) {
            plugin.getLogger().severe("НЕ ВОЗВРАЩЕНА оплата " + amount + " игроку " + player.getUniqueId()
                    + " за регион " + region.getId() + "; требуется ручная компенсация.");
        }
    }

    public boolean buy(Player buyer, Region region) {
        if (region == null) return false;
        try (Region.Operation op = region.tryOperation()) {
            if (op == null || !live(region) || region.isOwner(buyer.getUniqueId())) return false;
            RegionSale sale = sales.get(region.getId());
            if (sale == null || !region.isOwner(sale.sellerId()) || !priceValid(sale.price())) return false;
            if (RegionEvents.fireTransfer(region, buyer, buyer.getUniqueId(), buyer.getName())) return false;
            if (!live(region) || sales.get(region.getId()) != sale || !region.isOwner(sale.sellerId())) return false;
            double tax = plugin.getConfigManager().getConfig().getDouble("market.sell.tax-percent", 0);
            if (!Double.isFinite(tax)) return false;
            double payout = sale.price() * (100 - Math.max(0, Math.min(100, tax))) / 100;
            if (!plugin.getVaultHook().takeMoney(buyer, sale.price())) return false;
            if (!plugin.getVaultHook().giveMoney(Bukkit.getOfflinePlayer(sale.sellerId()), payout)) {
                refund(buyer, sale.price(), region);
                return false;
            }
            // участники и баны остаются — покупатель сам решит, кого оставить
            if (!plugin.getRegionManager().transferWithin(region, buyer.getUniqueId(), buyer.getName(), op,
                    plugin.getRegionManager().configuredRole("market.sell.seller-role", ""))) {
                // приват исчез между оплатой и передачей — деньги возвращаем покупателю
                refund(buyer, sale.price(), region);
                plugin.getLogger().severe("Покупка " + region.getId() + " сорвалась после оплаты: продавцу "
                        + sale.sellerId() + " уже выплачено " + payout + ", покупателю возвращено " + sale.price() + ".");
                return false;
            }
            return true;
        }
    }

    public boolean offerForRent(Region region, Player owner, double price, int minutes) {
        if (region == null || !priceValid(price) || minutes <= 0) return false;
        try (Region.Operation op = region.tryOperation()) {
            if (op == null || !live(region) || !region.isOwner(owner.getUniqueId())) return false;
            RegionRental previous = rentals.get(region.getId());
            // Даже истёкшая аренда сначала должна пройти штатное снятие участника.
            if (previous != null && previous.tenantId() != null) return false;
            RegionRental rental = new RegionRental(region.getId(), owner.getUniqueId(), owner.getName(), price, minutes, null, null, 0);
            rentals.put(region.getId(), rental);
            plugin.getRegionStorage().saveRental(rental);
            return true;
        }
    }

    private boolean releaseTenant(Region region, RegionRental rental) {
        if (rental.tenantId() == null) return true;
        if (RegionEvents.fireMemberChange(region, null, rental.tenantId(), rental.tenantName(),
                RegionMemberChangeEvent.Action.UNTRUST, null)) return false;
        if (!live(region) || rentals.get(region.getId()) != rental) return false;
        region.removeMember(rental.tenantId());
        plugin.getRegionStorage().save(region);
        Player tenant = Bukkit.getPlayer(rental.tenantId());
        if (tenant != null) plugin.getSchedulers().runAtEntity(tenant, () -> tenant.sendMessage(
                plugin.getLanguageManager().getMessage("rent_expired", "%id%", region.getShortId())));
        return true;
    }

    public boolean cancelRental(Region region) {
        if (region == null) return false;
        try (Region.Operation op = region.tryOperation()) {
            if (op == null) return false;
            RegionRental rental = rentals.get(region.getId());
            if (rental == null || !releaseTenant(region, rental)) return false;
            rentals.remove(region.getId());
            plugin.getRegionStorage().deleteRental(region.getId());
            return true;
        }
    }

    public boolean takeRent(Player tenant, Region region) {
        if (region == null) return false;
        try (Region.Operation op = region.tryOperation()) {
            if (op == null || !live(region) || region.isOwner(tenant.getUniqueId()) || region.isBanned(tenant.getUniqueId())) return false;
            RegionRental rental = rentals.get(region.getId());
            if (rental == null || !region.isOwner(rental.ownerId()) || !priceValid(rental.price()) || rental.durationMinutes() <= 0) return false;
            boolean extend = tenant.getUniqueId().equals(rental.tenantId()) && region.getMember(tenant.getUniqueId()).isPresent();
            if (rental.isRented() && !extend) return false;
            if (!extend && rental.tenantId() != null && !releaseTenant(region, rental)) return false;
            TrustLevel level = rentTrustLevel();
            if (!extend && RegionEvents.fireMemberChange(region, null, tenant.getUniqueId(), tenant.getName(),
                    RegionMemberChangeEvent.Action.TRUST, level)) return false;
            if (!live(region) || !region.isOwner(rental.ownerId()) || rentals.get(region.getId()) != rental
                    || region.isBanned(tenant.getUniqueId())) return false;
            if (!plugin.getVaultHook().takeMoney(tenant, rental.price())) return false;
            if (!plugin.getVaultHook().giveMoney(Bukkit.getOfflinePlayer(rental.ownerId()), rental.price())) {
                refund(tenant, rental.price(), region);
                return false;
            }
            long until = (extend ? Math.max(System.currentTimeMillis(), rental.rentedUntil()) : System.currentTimeMillis())
                    + TimeUnit.MINUTES.toMillis(rental.durationMinutes());
            if (!extend) region.setMember(tenant.getUniqueId(), tenant.getName(), level);
            RegionRental updated = new RegionRental(region.getId(), rental.ownerId(), rental.ownerName(), rental.price(),
                    rental.durationMinutes(), tenant.getUniqueId(), tenant.getName(), until);
            rentals.put(region.getId(), updated);
            plugin.getRegionStorage().saveRental(updated);
            plugin.getRegionStorage().save(region);
            return true;
        }
    }

    public TrustLevel rentTrustLevel() {
        TrustLevel role = plugin.getRegionManager().configuredRole("market.rent.trust-level", "container");
        return role != null ? role : TrustLevel.defaultRole();
    }

    public void expireRentals() {
        for (RegionRental rental : rentals.values()) {
            if (rental.tenantId() == null || rental.rentedUntil() > System.currentTimeMillis()) continue;
            Region region = plugin.getRegionManager().getById(rental.regionId());
            if (region == null) continue;
            var core = region.getCoreLocation();
            if (core != null) plugin.getSchedulers().runAtLocation(core, () -> expireRental(region, rental));
        }
    }

    private void expireRental(Region region, RegionRental rental) {
        try (Region.Operation op = region.tryOperation()) {
            if (op == null || !live(region) || rentals.get(region.getId()) != rental || !releaseTenant(region, rental)) return;
            RegionRental freed = new RegionRental(region.getId(), rental.ownerId(), rental.ownerName(), rental.price(),
                    rental.durationMinutes(), null, null, 0);
            rentals.put(region.getId(), freed);
            plugin.getRegionStorage().saveRental(freed);
        }
    }
}
