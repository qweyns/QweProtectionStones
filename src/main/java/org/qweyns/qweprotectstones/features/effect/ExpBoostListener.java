package org.qweyns.qweprotectstones.features.effect;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.TrustLevel;

public class ExpBoostListener implements Listener {

    private final QweProtectStones plugin;

    public ExpBoostListener(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getDroppedExp() <= 0) return;

        // считаем по месту смерти моба, не по позиции игрока

        if (!hasExpBoost(killer, event.getEntity().getLocation())) return;
        event.setDroppedExp(scale(event.getDroppedExp()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getExpToDrop() <= 0) return;
        if (!hasExpBoost(event.getPlayer(), event.getBlock().getLocation())) return;

        event.setExpToDrop(scale(event.getExpToDrop()));
    }

    private int scale(int exp) {
        double multiplier = plugin.getConfigManager().getExpBoostMultiplier();
        if (multiplier <= 0) return exp;

        return (int) Math.min(Integer.MAX_VALUE, Math.round(exp * multiplier));
    }

    private boolean hasExpBoost(Player player, Location location) {
        Region region = plugin.getRegionManager().getRegionAt(location);
        if (region == null) return false;
        if (!plugin.getProtectionService().has(region, player, TrustLevel.ACCESS)) return false;

        return region.hasEffect("EXP_BOOST");
    }
}
