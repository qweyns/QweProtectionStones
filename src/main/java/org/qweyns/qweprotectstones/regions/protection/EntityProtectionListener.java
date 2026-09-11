package org.qweyns.qweprotectstones.regions.protection;

import org.bukkit.entity.Animals;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.config.Tunables;

public class EntityProtectionListener implements Listener {

    private final ProtectionService protection;

    public EntityProtectionListener(QweProtectStones plugin) {
        this.protection = plugin.getProtectionService();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Region region = protection.regionAt(victim.getLocation());
        if (region == null) return;

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {

            return;
        }

        if (victim instanceof Player) {
            if (!protection.flag(region, RegionFlag.PVP)) {
                protection.notifyDenied(attacker, region);
                event.setCancelled(true);
            }
            return;
        }

        // враждебных бить можно всегда, иначе приват ферма мобов
        if (victim instanceof Monster) return;

        if (protection.has(region, attacker, protection.requiredFor(Tunables.TrustAction.ENTITY))) return;

        boolean protectedVictim = victim instanceof Animals || victim instanceof Villager || victim instanceof Tameable;
        if (protectedVictim && !protection.flag(region, RegionFlag.ANIMAL_PROTECTION)) return;

        protection.notifyDenied(attacker, region);
        event.setCancelled(true);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) return player;

        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player thrower)) return;

        // зельем не обойти запрет pvp
        event.getAffectedEntities().removeIf(entity -> {
            if (!(entity instanceof Player target) || target.equals(thrower)) return false;

            Region region = protection.regionAt(target.getLocation());
            return region != null && !protection.flag(region, RegionFlag.PVP);
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Region region = protection.regionAt(event.getEntity().getLocation());
        if (region == null) return;

        Player remover = resolveAttacker(event.getRemover());
        if (remover == null) {
            if (!protection.flag(region, RegionFlag.MOB_GRIEFING)) event.setCancelled(true);
            return;
        }

        if (!protection.has(region, remover, protection.requiredFor(Tunables.TrustAction.ENTITY))) {
            protection.notifyDenied(remover, region);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() == null) return;

        if (protection.denyBuild(event.getPlayer(), event.getEntity().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Player attacker = resolveAttacker(event.getAttacker());
        if (attacker == null) return;

        if (protection.denyInteract(attacker, event.getVehicle().getLocation(), protection.requiredFor(Tunables.TrustAction.ENTITY))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Player attacker = resolveAttacker(event.getAttacker());
        if (attacker == null) return;

        if (protection.denyInteract(attacker, event.getVehicle().getLocation(), protection.requiredFor(Tunables.TrustAction.ENTITY))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Region region = protection.regionAt(event.getLocation());
        if (region == null) return;

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.BREEDING) {
            return;
        }

        boolean monster = event.getEntity() instanceof Monster;
        RegionFlag flag = monster ? RegionFlag.MONSTER_SPAWNING : RegionFlag.ANIMAL_SPAWNING;
        if (!protection.flag(region, flag)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Region region = protection.regionAt(event.getItem().getLocation());
        if (region == null) return;

        if (protection.has(region, player, protection.requiredFor(Tunables.TrustAction.INTERACT))) return;
        if (!protection.flag(region, RegionFlag.ITEM_PICKUP)) event.setCancelled(true);
    }
}
