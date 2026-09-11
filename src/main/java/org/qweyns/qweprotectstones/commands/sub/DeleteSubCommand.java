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

/** Удаление своего привата командой, с подтверждением. */
public class DeleteSubCommand extends AbstractRegionSubCommand {


    /** Ожидающие подтверждения: игрок -> (приват, время запроса). */
    private final Map<UUID, PendingDelete> pending = new ConcurrentHashMap<>();

    private record PendingDelete(UUID regionId, long requestedAt) {
    }

    public DeleteSubCommand(QweProtectStones plugin) {
        super(plugin);
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

        // Удаление необратимо, поэтому спрашиваем подтверждение той же командой.
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
        returnCoreBlock(player, region);

        player.sendMessage(plugin.getLanguageManager().getMessage("region_removed"));
    }

    /** Убираем блок-ядро из мира и возвращаем его владельцу. */
    private void returnCoreBlock(Player player, Region region) {
        Location core = region.getCoreLocation();
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        if (core == null || type == null) return;

        if (core.getBlock().getType() == type.material()) {
            core.getBlock().setType(Material.AIR);
        }
        if (!type.returnBlockOnRemove()) return;

        var leftovers = player.getInventory().addItem(new org.bukkit.inventory.ItemStack(type.material()));
        leftovers.values().forEach(item -> core.getWorld().dropItemNaturally(core, item));
    }
}
