package org.qweyns.qweprotectstones.regions.protection;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.config.Tunables;
import org.qweyns.qweprotectstones.regions.Region;

/** Обходы защиты, которые не ловятся обычными событиями блоков и клика. */
public class ExtraProtectionListener implements Listener {

    private final QweProtectStones plugin;
    private final ProtectionService protection;

    public ExtraProtectionListener(QweProtectStones plugin) {
        this.plugin = plugin;
        this.protection = plugin.getProtectionService();
    }

    private boolean deny(Player player, Location at, Tunables.TrustAction action, String verb) {
        Region region = protection.regionAt(at);
        if (region == null || protection.can(region, player, action)) return false;
        protection.notifyDenied(player, region, verb);
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (deny(event.getPlayer(), event.getEntity().getLocation(), plugin.getTunables().interactRules().leash(), "interact"))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (deny(event.getPlayer(), event.getEntity().getLocation(), plugin.getTunables().interactRules().shear(), "interact"))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        if (deny(event.getPlayer(), event.getEntity().getLocation(), plugin.getTunables().interactRules().bucketEntity(), "interact"))
            event.setCancelled(true);
    }

    /** Стрелы и прочие снаряды игрока: кнопки, мишени, хорус, горшки, сталактиты. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Block block = event.getHitBlock();
        if (block == null || !(event.getEntity().getShooter() instanceof Player shooter)) return;
        Tunables.TrustAction action = plugin.getTunables().interactRules().forProjectile(block.getType());
        if (action == null) return;
        Region region = protection.regionAt(block.getLocation());
        if (region != null && !protection.can(region, shooter, action)) event.setCancelled(true);
    }

    /** Поджог TNT в чужом привате огнивом, горящей стрелой и т.п. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent event) {
        Entity primer = event.getPrimingEntity();
        Player player = primer instanceof Player direct ? direct
                : primer instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null;
        if (player == null) return;
        if (deny(player, event.getBlock().getLocation(), plugin.getTunables().interactRules().tntPrime(), "build"))
            event.setCancelled(true);
    }

    /** Вход в приват на транспорте (лодка, лошадь, вагонетка) подчиняется тем же правилам входа. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleMove(VehicleMoveEvent event) {
        Location from = event.getFrom(), to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;
        if (event.getVehicle().getPassengers().isEmpty()) return;
        Region target = protection.regionAt(to);
        if (target == null || target.equals(protection.regionAt(from))) return;

        for (Entity passenger : event.getVehicle().getPassengers()) {
            if (!(passenger instanceof Player player)) continue;
            if (plugin.getRegionMovementListener().canEnter(player, target)) continue;
            event.getVehicle().removePassenger(player);
            player.teleportAsync(from.clone().add(0, 0.5, 0));
            protection.sendThrottled(player, plugin.getLanguageManager().getMessage("region_entry_denied"));
        }
    }
}
