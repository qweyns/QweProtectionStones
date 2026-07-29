package org.qweyns.qweprotectstones.listeners;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionManager;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.event.RegionDeleteEvent;

/**
 * Рождение и смерть привата: установка блока-ядра создаёт приват, разрушение
 * ядра владельцем — удаляет. Заменяет собой PSCreateEvent/PSRemoveEvent.
 */
public class RegionLifecycleListener implements Listener {

    private final QweProtectStones plugin;

    public RegionLifecycleListener(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        plugin.getHologramManager().restoreHolograms();
    }

    // ------------------------------------------------------------------
    // Создание
    // ------------------------------------------------------------------

    /**
     * Приоритет HIGH: даём другим плагинам защиты сначала отменить установку,
     * и только потом создаём приват.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        RegionType type = plugin.getRegionTypes().byMaterial(block.getType());
        if (type == null) return;

        Player player = event.getPlayer();

        // Shift позволяет поставить блок как обычную декорацию, без создания привата.
        if (player.isSneaking() && plugin.getConfigManager().isSneakPlacesPlainBlock()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("region_placed_as_block"));
            return;
        }

        // Ограничение частоты: иначе стаком блоков создаётся десяток приватов за секунду.
        long wait = plugin.getRateLimiter().secondsRemaining(player);
        if (wait > 0) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLanguageManager().getMessage("region_cooldown", "%seconds%", String.valueOf(wait)));
            return;
        }

        RegionManager.CreateResult result = plugin.getRegionManager().createRegion(player, type, block.getLocation());
        if (!result.successful()) {
            event.setCancelled(true);
            sendCreationFailure(player, result, type);
            return;
        }

        Region region = result.region();
        Location coreLocation = block.getLocation();
        plugin.getRateLimiter().markCreated(player);

        plugin.getVisualManager().playEffect(coreLocation, type.id(), "create");
        plugin.getVisualManager().showBoundary(region, "create");
        plugin.getHologramManager().createOrUpdateHologram(region);
        plugin.getAutoAddManager().applyToRegion(player, region);
        plugin.getDynmapIntegration().update(region);

        player.sendMessage(plugin.getLanguageManager().getMessage("region_created",
                "%size%", type.widthX() + "x" + type.widthZ(),
                "%durability%", String.valueOf(region.getDurability())));
    }

    private void sendCreationFailure(Player player, RegionManager.CreateResult result, RegionType type) {
        switch (result.status()) {
            case OVERLAP -> player.sendMessage(plugin.getLanguageManager().getMessage("region_overlap",
                    "%owner%", result.blockingRegion() == null ? "" : result.blockingRegion().getOwnerName()));
            case TOO_CLOSE -> player.sendMessage(plugin.getLanguageManager().getMessage("region_too_close",
                    "%distance%", String.valueOf(type.minDistanceToOthers())));
            case LIMIT_REACHED -> player.sendMessage(plugin.getLanguageManager().getMessage("region_limit_reached",
                    "%limit%", String.valueOf(result.limit())));
            case NO_PERMISSION -> player.sendMessage(plugin.getLanguageManager().getMessage("no_permission"));
            case WORLD_DISABLED -> player.sendMessage(plugin.getLanguageManager().getMessage("region_world_disabled"));
            case CANCELLED -> { /* другой плагин уже объяснил игроку причину */ }
            default -> { /* успех сюда не попадает */ }
        }
    }

    // ------------------------------------------------------------------
    // Удаление
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Region region = plugin.getRegionManager().getRegionAt(block.getLocation());
        if (region == null || !region.isCore(block.getLocation())) return;

        Player player = event.getPlayer();

        // Ядро сносит только владелец: даже управляющий не должен удалять чужой приват.
        boolean allowed = region.isOwner(player.getUniqueId()) || plugin.getProtectionService().bypasses(player);
        if (!allowed) {
            event.setCancelled(true);
            plugin.getProtectionService().notifyDenied(player, region);
            return;
        }

        if (!plugin.getRegionManager().deleteRegion(region, RegionDeleteEvent.Reason.BROKEN, player)) {
            event.setCancelled(true);
            return;
        }

        cleanupVisuals(region);

        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        if (type != null && !type.returnBlockOnRemove()) {
            event.setDropItems(false);
        } else if (type != null && player.getGameMode() != GameMode.CREATIVE) {
            // Блок возвращаем сами, чтобы он не потерялся из-за настроек дропа.
            event.setDropItems(false);
            giveOrDrop(player, block.getLocation(), type.material());
        }

        player.sendMessage(plugin.getLanguageManager().getMessage("region_removed"));
    }

    private void giveOrDrop(Player player, Location location, Material material) {
        ItemStack stack = new ItemStack(material);
        var leftovers = player.getInventory().addItem(stack);

        // Инвентарь полон — кладём под ноги, а не выбрасываем в пустоту.
        leftovers.values().forEach(item -> location.getWorld().dropItemNaturally(location, item));
    }

    /** Общая уборка после удаления привата любым способом. */
    public void cleanupVisuals(Region region) {
        plugin.getHologramManager().removeHologram(region.getId());
        plugin.getVisualManager().showBoundary(region, "remove");
        plugin.getVisualManager().removeGlow(region.getId());
        plugin.getPenaltyManager().removeRegion(region.getId());
        plugin.getDynmapIntegration().remove(region);

        Location core = region.getCoreLocation();
        if (core != null) plugin.getVisualManager().playEffect(core, region.getTypeId(), "remove");
    }
}
