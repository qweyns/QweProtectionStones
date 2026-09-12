package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.event.RegionDeleteEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DeleteSubCommand extends AbstractRegionSubCommand implements org.bukkit.event.Listener {

    private final Map<UUID, PendingDelete> pending = new ConcurrentHashMap<>();

    private record PendingDelete(UUID regionId, long requestedAt) {
    }

    public DeleteSubCommand(QweProtectStones plugin) {
        super(plugin);
        // карта подтверждений не должна переживать выход игрока, как остальные UUID-карты
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @org.bukkit.event.EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public String permission() {
        return QweProtectStones.PERMISSION_PREFIX + ".delete";
    }

    @Override
    public String name() {
        return "delete";
    }

    @Override
    public List<String> aliases() {
        return List.of("remove", "unregion", "unclaim", "abandon");
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        Region region = regionUnderFeet(player);
        if (region == null) return;

        boolean allowed = region.isOwner(player.getUniqueId()) || plugin.getProtectionService().bypasses(player);
        if (!allowed) {
            player.sendMessage(plugin.getLanguageManager().getMessage("not_an_owner"));
            return;
        }

        // осада должна догореть, снос командой её тоже сбрасывал бы

        if (plugin.getConfigManager().isSiegeEnabled()
                && plugin.getConfigManager().isCoreBreakDeniedUnderAttack()
                && region.isUnderSiege(plugin.getTunables().siegeWindowMs())
                && !plugin.getProtectionService().bypasses(player)) {
            long leftMs = region.getLastAttackAt() + plugin.getTunables().siegeWindowMs()
                    - System.currentTimeMillis();
            player.sendMessage(plugin.getLanguageManager().getMessage("core_break_siege",
                    "%seconds%", String.valueOf(Math.max(1, (leftMs + 999) / 1000))));
            return;
        }

        // подтверждение той же командой
        PendingDelete confirmation = pending.get(player.getUniqueId());
        boolean confirmed = confirmation != null
                && confirmation.regionId().equals(region.getId())
                && System.currentTimeMillis() - confirmation.requestedAt() < plugin.getTunables().deleteConfirmMs();

        if (!confirmed) {
            pending.put(player.getUniqueId(), new PendingDelete(region.getId(), System.currentTimeMillis()));
            plugin.getLanguageManager().sendList(player, "delete_confirm",
                    "%id%", region.getShortId(),
                    "%seconds%", String.valueOf(plugin.getTunables().deleteConfirmMs() / 1000),
                    "%command%", plugin.getConfigManager().getCommandName());
            plugin.getVisualManager().showBoundary(region, "remove");
            return;
        }

        pending.remove(player.getUniqueId());

        if (!plugin.getRegionManager().deleteRegion(region, RegionDeleteEvent.Reason.COMMAND, player)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("delete_cancelled"));
            return;
        }

        plugin.getRegionLifecycleListener().cleanupVisuals(region);
        returnCoreBlock(region);

        plugin.getCriticalFileLogger().log("PLAYER_DELETE",
                "by=" + player.getName() + " region=" + region.getShortId() + " owner=" + region.getOwnerName());
        player.sendMessage(plugin.getLanguageManager().getMessage("region_removed"));
    }

    private void returnCoreBlock(Region region) {
        Location core = region.getCoreLocation();
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        if (core == null || type == null) return;

        if (core.getBlock().getType() == type.material()) {
            core.getBlock().setType(Material.AIR);
        }
        if (!type.returnBlockOnRemove()) return;

        core.getWorld().dropItemNaturally(core,
                org.qweyns.qweprotectstones.utils.RegionItems.returnCore(plugin, type, region));
    }
}
