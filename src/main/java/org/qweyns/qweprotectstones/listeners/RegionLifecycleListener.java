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

        // Предмет с PDC-тегом типа (выдан /qps give или возвращён при поломке)
        // создаёт приват именного типа, даже если по материалу он не опознан.
        RegionType tagged = taggedType(event.getItemInHand(), block);
        RegionType type = tagged != null ? tagged : plugin.getRegionTypes().byMaterial(block.getType());
        if (type == null) return;

        // «Покупной» тип работает только предметом с тегом: добытый в мире
        // блок того же материала остаётся обычным блоком, без создания привата.
        if (type.source() == org.qweyns.qweprotectstones.regions.RegionSource.COMMAND && tagged == null) return;

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

        // Прочность, сохранённая в предмете (перенос ядра на новое место),
        // переносится на новый приват — с ограничением по максимуму типа.
        int carriedDurability = taggedDurability(event.getItemInHand());
        if (carriedDurability > 0) {
            region.setDurability(Math.min(carriedDurability, region.getMaxDurability()));
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
            giveOrDrop(player, block.getLocation(), type, region);
        }

        player.sendMessage(plugin.getLanguageManager().getMessage("region_removed"));
    }

    /**
     * Возврат блока ядра. Если включено {@code settings.return-durability},
     * в предмет записывается PDC-тег с текущей прочностью привата — поставив
     * ядро заново, владелец не потеряет прокачку.
     */
    private void giveOrDrop(Player player, Location location, RegionType type, Region region) {
        boolean tags = plugin.getConfigManager().getConfig().getBoolean("settings.core-item-tags", true);
        // «Покупной» тип помечаем тегом всегда: без тега возвращённый блок
        // больше не создал бы приват — игрок потерял бы покупку.
        boolean tagType = tags || type.source() == org.qweyns.qweprotectstones.regions.RegionSource.COMMAND;
        Integer durability = region != null
                && plugin.getConfigManager().getConfig().getBoolean("settings.return-durability", true)
                && tags
                ? region.getDurability() : null;

        ItemStack stack = org.qweyns.qweprotectstones.utils.RegionItems.core(
                plugin, type, 1, durability, tagType);

        var leftovers = player.getInventory().addItem(stack);

        // Инвентарь полон — кладём под ноги, а не выбрасываем в пустоту.
        leftovers.values().forEach(item -> location.getWorld().dropItemNaturally(location, item));
    }

    /** Тип из PDC-тега предмета; null, если тега нет или материал не совпадает. */
    private RegionType taggedType(ItemStack item, Block block) {
        String typeId = readTag(item, typeKey());
        if (typeId == null) return null;

        RegionType type = plugin.getRegionTypes().byId(typeId.toLowerCase(java.util.Locale.ROOT));
        // Тег валиден только для совпадающего материала: иначе чужой предмет
        // превращал бы любой блок в чужой тип привата.
        return type != null && type.material() == block.getType() ? type : null;
    }

    /** Прочность из PDC-тега предмета или 0. */
    private int taggedDurability(ItemStack item) {
        Integer value = getIntTag(item, durabilityKey());
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

    private org.bukkit.NamespacedKey typeKey() {
        return org.qweyns.qweprotectstones.utils.RegionItems.typeKey(plugin);
    }

    private org.bukkit.NamespacedKey durabilityKey() {
        return org.qweyns.qweprotectstones.utils.RegionItems.durabilityKey(plugin);
    }

    /** Общая уборка после удаления привата любым способом. */
    public void cleanupVisuals(Region region) {
        plugin.getHologramManager().removeHologram(region.getId());
        plugin.getVisualManager().showBoundary(region, "remove");
        plugin.getVisualManager().removeGlow(region.getId());
        plugin.getPenaltyManager().removeRegion(region.getId());
        plugin.getDynmapIntegration().remove(region);
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().remove(region);

        Location core = region.getCoreLocation();
        if (core != null) plugin.getVisualManager().playEffect(core, region.getTypeId(), "remove");
    }
}
