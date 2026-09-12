package org.qweyns.qweprotectstones.listeners;

import org.bukkit.GameMode;
import org.bukkit.Location;
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

public class RegionLifecycleListener implements Listener {

    private final QweProtectStones plugin;

    // ключи PDC неизменяемы — создаём один раз, а не на каждое событие
    private final org.bukkit.NamespacedKey typeKey;
    private final org.bukkit.NamespacedKey durabilityKey;
    private final org.bukkit.NamespacedKey penaltyUntilKey;
    private final org.bukkit.NamespacedKey lastAttackKey;

    public RegionLifecycleListener(QweProtectStones plugin) {
        this.plugin = plugin;
        this.typeKey = org.qweyns.qweprotectstones.utils.RegionItems.typeKey(plugin);
        this.durabilityKey = org.qweyns.qweprotectstones.utils.RegionItems.durabilityKey(plugin);
        this.penaltyUntilKey = org.qweyns.qweprotectstones.utils.RegionItems.penaltyUntilKey(plugin);
        this.lastAttackKey = org.qweyns.qweprotectstones.utils.RegionItems.lastAttackKey(plugin);
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        plugin.getHologramManager().restoreHolograms();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();

        RegionType tagged = taggedType(event.getItemInHand(), block);
        RegionType type = tagged != null ? tagged : plugin.getRegionTypes().byMaterial(block.getType());
        if (type == null) return;

        // restrict-obtaining: без тега блок остаётся просто блоком

        if (type.restrictObtaining() && tagged == null) return;

        Player player = event.getPlayer();

        if (player.isSneaking() && type.sneakPlacesPlainBlock()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("region_placed_as_block"));
            return;
        }

        // рейт-лимит, стаком блоков плодятся приваты
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

        // прочность из PDC предмета переносится, с потолком по типу

        int carriedDurability = taggedDurability(event.getItemInHand());
        if (carriedDurability > 0) {
            region.setDurability(Math.min(carriedDurability, region.getMaxDurability()));
        }

        // штраф и момент последней атаки едут с предметом: «сломал-поставил» осаду не обнуляет

        long carriedAttack = taggedLong(event.getItemInHand(), lastAttackKey);
        if (carriedAttack > 0) {
            region.restoreStats(region.getAttackCount(), carriedAttack, region.getLastAttackerName());
        }
        long carriedPenalty = taggedLong(event.getItemInHand(), penaltyUntilKey);
        if (carriedPenalty > System.currentTimeMillis()) {
            region.setPenaltyUntil(carriedPenalty);
        }
        if (carriedDurability > 0 || carriedAttack > 0 || carriedPenalty > 0) {
            plugin.getRegionStorage().save(region);
        }

        plugin.getVisualManager().playEffect(coreLocation, type.id(), "create");
        plugin.getVisualManager().showBoundary(region, "create");
        plugin.getHologramManager().createOrUpdateHologram(region);
        plugin.getAutoAddManager().applyToRegion(player, region);
        plugin.getDynmapIntegration().update(region);
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().update(region);

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
            case CANCELLED -> {  }
            default -> {  }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Region region = plugin.getRegionManager().getRegionAt(block.getLocation());
        if (region == null || !region.isCore(block.getLocation())) return;

        Player player = event.getPlayer();

        // право destroy отдельно, можно продать приват без сноса

        boolean allowed = (region.isOwner(player.getUniqueId())
                && player.hasPermission(QweProtectStones.PERMISSION_PREFIX + ".destroy"))
                || plugin.getProtectionService().bypasses(player);
        if (!allowed) {
            event.setCancelled(true);
            if (region.isOwner(player.getUniqueId())) {
                player.sendMessage(plugin.getLanguageManager().getMessage("no_permission"));
            } else {
                plugin.getProtectionService().notifyDenied(player, region);
            }
            return;
        }

        // снести ядро и поставить заново — значит сбросить осаду, не даём

        if (plugin.getConfigManager().isSiegeEnabled()
                && plugin.getConfigManager().isCoreBreakDeniedUnderAttack()
                && region.isUnderSiege(plugin.getTunables().siegeWindowMs())
                && !plugin.getProtectionService().bypasses(player)) {
            event.setCancelled(true);
            long leftMs = region.getLastAttackAt() + plugin.getTunables().siegeWindowMs()
                    - System.currentTimeMillis();
            player.sendMessage(plugin.getLanguageManager().getMessage("core_break_siege",
                    "%seconds%", String.valueOf(Math.max(1, (leftMs + 999) / 1000))));
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

            event.setDropItems(false);
            dropCore(block.getLocation(), type, region);
        }

        player.sendMessage(plugin.getLanguageManager().getMessage("region_removed"));
    }

    private void dropCore(Location location, RegionType type, Region region) {
        org.bukkit.World world = location.getWorld();
        if (world == null) return;

        world.dropItemNaturally(location,
                org.qweyns.qweprotectstones.utils.RegionItems.returnCore(plugin, type, region));
    }

    private RegionType taggedType(ItemStack item, Block block) {
        String typeId = readTag(item, typeKey);
        if (typeId == null) return null;

        RegionType type = plugin.getRegionTypes().byId(typeId.toLowerCase(java.util.Locale.ROOT));
        // тег валиден только для своего материала

        return type != null && type.material() == block.getType() ? type : null;
    }

    private int taggedDurability(ItemStack item) {
        Integer value = getIntTag(item, durabilityKey);
        return value != null ? value : 0;
    }

    private String readTag(ItemStack item, org.bukkit.NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING);
    }

    private Integer getIntTag(ItemStack item, org.bukkit.NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.INTEGER);
    }

    private long taggedLong(ItemStack item, org.bukkit.NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return 0L;
        Long value = item.getItemMeta().getPersistentDataContainer()
                .get(key, org.bukkit.persistence.PersistentDataType.LONG);
        return value != null ? value : 0L;
    }

    public void cleanupVisuals(Region region) {
        plugin.getHologramManager().removeHologram(region.getId());
        plugin.getVisualManager().showBoundary(region, "remove");
        plugin.getVisualManager().removeGlow(region.getId());
        plugin.getDynmapIntegration().remove(region);
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().remove(region);

        Location core = region.getCoreLocation();
        if (core != null) plugin.getVisualManager().playEffect(core, region.getTypeId(), "remove");
    }
}
