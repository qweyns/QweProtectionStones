package org.qweyns.qweprotectstones.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionItems {

    private RegionItems() {
    }

    // ключ зависит только от имени плагина — кеш безопасен и переживает reload
    private static final Map<String, NamespacedKey> KEYS = new ConcurrentHashMap<>();

    private static NamespacedKey key(QweProtectStones plugin, String name) {
        return KEYS.computeIfAbsent(plugin.getName().toLowerCase(Locale.ROOT) + ':' + name,
                ignored -> new NamespacedKey(plugin, name));
    }

    public static NamespacedKey typeKey(QweProtectStones plugin) {
        return key(plugin, "core-type");
    }

    public static NamespacedKey durabilityKey(QweProtectStones plugin) {
        return key(plugin, "core-durability");
    }

    public static NamespacedKey penaltyUntilKey(QweProtectStones plugin) {
        return key(plugin, "core-penalty-until");
    }

    public static NamespacedKey lastAttackKey(QweProtectStones plugin) {
        return key(plugin, "core-last-attack");
    }

    public static ItemStack core(QweProtectStones plugin, RegionType type, int amount,
                                 Integer carriedDurability, boolean tagType, boolean styled) {
        return core(plugin, type, amount, carriedDurability, tagType, styled, 0L, 0L);
    }

    public static ItemStack core(QweProtectStones plugin, RegionType type, int amount,
                                 Integer carriedDurability, boolean tagType, boolean styled,
                                 long penaltyUntil, long lastAttackAt) {
        ItemStack stack = new ItemStack(type.material(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        if (tagType) {
            meta.getPersistentDataContainer().set(
                    typeKey(plugin), PersistentDataType.STRING, type.id());
        }
        if (carriedDurability != null && carriedDurability > 0) {
            meta.getPersistentDataContainer().set(
                    durabilityKey(plugin), PersistentDataType.INTEGER, carriedDurability);
        }
        if (penaltyUntil > System.currentTimeMillis()) {
            meta.getPersistentDataContainer().set(
                    penaltyUntilKey(plugin), PersistentDataType.LONG, penaltyUntil);
        }
        if (lastAttackAt > 0) {
            meta.getPersistentDataContainer().set(
                    lastAttackKey(plugin), PersistentDataType.LONG, lastAttackAt);
        }

        if (styled) {
            // %durability% накопленная или стартовая

            String durability = String.valueOf(carriedDurability != null && carriedDurability > 0
                    ? carriedDurability : type.startDurability());
            String maxDurability = String.valueOf(type.maxDurability());

            if (!type.itemName().isEmpty()) {
                meta.displayName(ColorUtil.formatItemComponent(
                        durabilityPlaceholders(type.itemName(), durability, maxDurability)));
            }
            if (!type.itemLore().isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : type.itemLore()) {
                    lore.add(ColorUtil.formatItemComponent(
                            durabilityPlaceholders(line, durability, maxDurability)));
                }
                meta.lore(lore);
            }

            if (type.itemGlow()) {
                meta.setEnchantmentGlintOverride(true);
            }
        }

        stack.setItemMeta(meta);
        return stack;
    }

    static String durabilityPlaceholders(String text, String durability, String maxDurability) {
        if (text == null || text.isEmpty()) return text;
        return text.replace("%durability%", durability)
                .replace("%max_durability%", maxDurability);
    }

    public static ItemStack returnCore(QweProtectStones plugin, RegionType type, Region region) {
        boolean tags = plugin.getConfigManager().getConfig().getBoolean("settings.core-item-tags", true);
        Integer durability = region != null
                && plugin.getConfigManager().getConfig().getBoolean("settings.return-durability", true)
                && tags
                ? region.getDurability() : null;
        boolean transferPenalty = tags && plugin.getConfigManager().isPenaltyTransferEnabled();
        long penaltyUntil = transferPenalty && region != null ? region.getPenaltyUntil() : 0L;
        long lastAttackAt = transferPenalty && region != null ? region.getLastAttackAt() : 0L;
        return core(plugin, type, 1, durability, tags || type.restrictObtaining(), type.restrictObtaining(),
                penaltyUntil, lastAttackAt);
    }

}
