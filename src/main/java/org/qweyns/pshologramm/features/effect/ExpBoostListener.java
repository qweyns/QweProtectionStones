package org.qweyns.pshologramm.features.effect;

import dev.espi.protectionstones.PSRegion;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.models.RegionData;

public class ExpBoostListener implements Listener {
    private final PSHologramm plugin;

    public ExpBoostListener(PSHologramm plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player player = event.getEntity().getKiller();
        applyExpBoost(player, event);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        applyExpBoost(event.getPlayer(), event);
    }

    private void applyExpBoost(Player player, org.bukkit.event.Event event) {
        PSRegion region = PSRegion.fromLocation(player.getLocation());
        if (region == null || (!region.isOwner(player.getUniqueId()) && !region.isMember(player.getUniqueId()))) return;

        RegionData rd = plugin.getStorageManager().getRegion(region.getId());
        if (rd == null || rd.getEffects() == null) return;

        boolean hasBoost = rd.getEffects().stream().anyMatch(eff -> eff.startsWith("EXP_BOOST"));

        if (hasBoost) {
            double multiplier = plugin.getConfigManager().getConfig().getDouble("settings.exp_boost_multiplier", 2.0);

            if (event instanceof EntityDeathEvent) {
                EntityDeathEvent e = (EntityDeathEvent) event;
                e.setDroppedExp((int) (e.getDroppedExp() * multiplier));
            } else if (event instanceof BlockBreakEvent) {
                BlockBreakEvent e = (BlockBreakEvent) event;
                e.setExpToDrop((int) (e.getExpToDrop() * multiplier));
            }
        }
    }
}
