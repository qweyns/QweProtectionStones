package org.qweyns.qweprotectstones.menus;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.UpgradeCost;

import java.util.List;
import java.util.Map;

/** Прокачка прочности: слоты меню upgrade, цена с учётом штрафа и оплата предметами. */
class MenuUpgrades {

    private final QweProtectStones plugin;
    private final MenuManager menus;

    MenuUpgrades(QweProtectStones plugin, MenuManager menus) {
        this.plugin = plugin;
        this.menus = menus;
    }

    void renderSlots(Player player, MenuHolder holder, Region region, FileConfiguration menuCfg, ItemStack[] contents) {
        List<Integer> slots = menuCfg.getIntegerList("upgrade_slots");
        ConfigurationSection template = menuCfg.getConfigurationSection("upgrade_item_template");
        if (slots.isEmpty() || template == null || region == null) return;

        ConfigurationSection maxTemplate = menuCfg.getConfigurationSection("max_upgrade_item_template");

        int currentDurability = region.getDurability();
        int maxDurability = region.getMaxDurability();
        Material upgradeMaterial = upgradeItemFor(region);

        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            if (slot < 0 || slot >= contents.length) continue;

            int targetLevel = currentDurability + i + 1;

            if (targetLevel > maxDurability) {
                if (maxTemplate != null) {
                    ItemStack maxItem = menus.getItemFactory().build(maxTemplate, player, region,
                            menus.dynamicPlaceholders("upgrade", region), "upgrade:max");
                    if (maxItem != null) contents[slot] = maxItem;
                }
                continue;
            }

            Map<String, String> extra = menus.dynamicPlaceholders("upgrade", region);
            extra.put("%level%", String.valueOf(targetLevel));
            extra.put("%cost%", String.valueOf(upgradeCost(region, currentDurability, targetLevel)));
            extra.put("%item%", "<translate:" + upgradeMaterial.translationKey() + ">");

            String signature = "upgrade|" + targetLevel + "|" + currentDurability + "/" + maxDurability
                    + "|" + upgradeMaterial.name();
            if (signature.equals(holder.slotSignatureAt(slot))) {
                contents[slot] = holder.slotStackAt(slot);
                continue;
            }

            ItemStack item = menus.getItemFactory().build(template, player, region, extra);
            if (item == null) continue;

            // withType, не setType, второй теряет метаданные

            item = item.withType(upgradeMaterial);
            item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), targetLevel)));
            contents[slot] = item;
            holder.ensureSlotCache(contents.length);
            holder.slotSignatures[slot] = signature;
            holder.slotStacks[slot] = item;
        }
    }

    boolean handleClick(Player player, MenuHolder holder, FileConfiguration menuCfg, int slot) {
        List<Integer> upgradeSlots = menuCfg.getIntegerList("upgrade_slots");
        int slotIndex = upgradeSlots.indexOf(slot);
        if (slotIndex < 0) return false;

        Region region = holder.region;
        if (region == null) return true;

        if (!plugin.getProtectionService().canManage(player, region)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("no_region_access",
                    "%level%", plugin.getLanguageManager().rawTemplate("trust_manager")));
            return true;
        }

        int currentDurability = region.getDurability();
        int targetLevel = currentDurability + slotIndex + 1;
        int maxDurability = region.getMaxDurability();

        if (targetLevel > maxDurability) {
            plugin.getTunables().menuDenied().playTo(player);
            return true;
        }

        long totalCost = upgradeCost(region, currentDurability, targetLevel);
        Material upgradeMaterial = upgradeItemFor(region);
        String itemName = "<translate:" + upgradeMaterial.translationKey() + ">";

        if (countItems(player, upgradeMaterial) < totalCost) {
            player.sendMessage(plugin.getLanguageManager().getMessage("upgrade_not_enough_items",
                    "%amount%", String.valueOf(totalCost), "%item%", itemName));
            plugin.getTunables().menuDenied().playTo(player);
            return true;
        }

        if (!removeItems(player, upgradeMaterial, totalCost)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("upgrade_not_enough_items",
                    "%amount%", String.valueOf(totalCost), "%item%", itemName));
            return true;
        }

        if (plugin.getPenaltyManager().hasPenalty(region)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("upgrade_penalty",
                    "%multiplier%", String.valueOf(plugin.getPenaltyManager().getPenaltyMultiplier())));
        }

        region.setDurability(targetLevel);
        plugin.getRegionStorage().saveNow(region);
        plugin.getHologramManager().createOrUpdateHologram(region);

        plugin.getTunables().menuSuccess().playTo(player);
        player.sendMessage(plugin.getLanguageManager().getMessage("upgrade_success", "%durability%", String.valueOf(targetLevel)));
        menus.render(player, holder, true);
        return true;
    }

    private long upgradeCost(Region region, int current, int target) {
        int penalty = plugin.getPenaltyManager().hasPenalty(region)
                ? plugin.getPenaltyManager().getPenaltyMultiplier()
                : 1;

        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        int multiplier = type != null && type.overridesUpgradeMultiplier()
                ? type.upgradeCostMultiplier()
                : plugin.getConfigManager().getUpgradeMultiplier();
        int tax = type != null && type.overridesUpgradeTax()
                ? type.upgradeTax()
                : plugin.getConfigManager().getUpgradeTax();

        return UpgradeCost.calculate(current, target, multiplier, tax, penalty);
    }

    Material upgradeItemFor(Region region) {
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        if (type == null || !type.overridesUpgradeItem()) return plugin.getConfigManager().getUpgradeItem();

        Material material = Material.matchMaterial(type.upgradeItem());
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("Тип " + type.id() + ": upgrade.item '" + type.upgradeItem()
                    + "' не является предметом — использую общий.");
            return plugin.getConfigManager().getUpgradeItem();
        }
        return material;
    }

    private long countItems(Player player, Material material) {
        long count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) count += stack.getAmount();
        }
        return count;
    }

    private boolean removeItems(Player player, Material material, long amount) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        long needed = amount;

        for (int i = 0; i < storage.length && needed > 0; i++) {
            ItemStack stack = storage[i];
            if (stack == null || stack.getType() != material) continue;

            if (stack.getAmount() <= needed) {
                needed -= stack.getAmount();
                storage[i] = null;
            } else {
                stack.setAmount((int) (stack.getAmount() - needed));
                needed = 0;
            }
        }
        if (needed > 0) return false;

        player.getInventory().setStorageContents(storage);
        player.updateInventory();
        return true;
    }
}
