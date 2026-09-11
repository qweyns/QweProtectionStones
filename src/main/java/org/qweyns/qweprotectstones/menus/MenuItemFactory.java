package org.qweyns.qweprotectstones.menus;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.SkullMeta;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.utils.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MenuItemFactory {

    private final QweProtectStones plugin;
    private final MenuPlaceholders placeholders;

    private final Map<String, ItemStack> staticCache = new ConcurrentHashMap<>();

    public MenuItemFactory(QweProtectStones plugin, MenuPlaceholders placeholders) {
        this.plugin = plugin;
        this.placeholders = placeholders;
    }

    public void clearCache() {
        staticCache.clear();
    }

    public ItemStack build(ConfigurationSection cfg, Player player, Region region, Map<String, String> extra) {
        return build(cfg, player, region, extra, null);
    }

    public ItemStack build(ConfigurationSection cfg, Player player, Region region,
                           Map<String, String> extra, String cacheKey) {
        boolean cacheable = cacheKey != null && isStatic(cfg);
        if (cacheable) {
            ItemStack cached = staticCache.get(cacheKey);
            if (cached != null) return cached.clone();
        }

        ItemStack item = create(cfg, player, region, extra);
        if (cacheable) staticCache.put(cacheKey, item.clone());
        return item;
    }

    private boolean isStatic(ConfigurationSection cfg) {
        if (MenuPlaceholders.isDynamic(cfg.getString("material"))) return false;
        if (MenuPlaceholders.isDynamic(cfg.getString("display_name"))) return false;

        for (String line : cfg.getStringList("lore")) {
            if (MenuPlaceholders.isDynamic(line)) return false;
        }
        return true;
    }

    private ItemStack create(ConfigurationSection cfg, Player player, Region region, Map<String, String> extra) {
        String matStr = cfg.getString("material", "STONE");
        if (matStr == null || matStr.isBlank()) matStr = "STONE";

        if (matStr.equalsIgnoreCase("%region_material%")) {
            RegionType type = region == null ? null : plugin.getRegionTypes().byId(region.getTypeId());
            matStr = type != null ? type.material().name() : "STONE";
        }

        ItemStack item = createBase(matStr, player);
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), cfg.getInt("amount", 1))));

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (cfg.contains("display_name")) {
            meta.displayName(ColorUtil.formatItemComponent(
                    placeholders.apply(player, cfg.getString("display_name"), region, extra)));
        }

        if (cfg.contains("lore")) {
            List<Component> lore = new ArrayList<>();
            for (String line : cfg.getStringList("lore")) {
                lore.add(ColorUtil.formatItemComponent(placeholders.apply(player, line, region, extra)));
            }
            meta.lore(lore);
        }

        if (cfg.getBoolean("unbreakable", false)) meta.setUnbreakable(true);
        if (cfg.contains("custom_model_data")) {
            // с 1.21.5 custom_model_data это компонент со списком float
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(java.util.List.of((float) cfg.getInt("custom_model_data")));
            meta.setCustomModelDataComponent(component);
        }

        applyEnchantments(cfg, meta);
        applyItemFlags(cfg, meta);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBase(String matStr, Player player) {
        if (matStr.startsWith("base64-")) return createBase64Skull(matStr.substring("base64-".length()));

        if (matStr.startsWith("head-")) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                String owner = matStr.substring("head-".length()).replace("%player_name%", player.getName());
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
                head.setItemMeta(meta);
            }
            return head;
        }

        Material material = Material.matchMaterial(matStr);
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("Неизвестный материал в меню: " + matStr);
            material = Material.STONE;
        }
        return new ItemStack(material);
    }

    private ItemStack createBase64Skull(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
        profile.setProperty(new ProfileProperty("textures", base64));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    private void applyEnchantments(ConfigurationSection cfg, ItemMeta meta) {
        if (!cfg.contains("enchantments")) return;

        for (String raw : cfg.getStringList("enchantments")) {
            String[] parts = raw.split(":");
            // fromString вернёт null вместо исключения
            NamespacedKey key = NamespacedKey.fromString(parts[0].toLowerCase(Locale.ROOT));
            if (key == null) {
                plugin.getLogger().warning("Некорректное имя зачарования в меню: " + parts[0]);
                continue;
            }

            Enchantment enchantment = io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT).get(key);
            if (enchantment == null) {
                plugin.getLogger().warning("Неизвестное зачарование в меню: " + parts[0]);
                continue;
            }

            int level = 1;
            if (parts.length > 1) {
                try {
                    level = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Некорректный уровень зачарования: " + raw);
                }
            }
            meta.addEnchant(enchantment, level, true);
        }
    }

    private void applyItemFlags(ConfigurationSection cfg, ItemMeta meta) {
        if (cfg.getBoolean("hide_attributes", false)) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.setAttributeModifiers(com.google.common.collect.ArrayListMultimap.create());
        }
        if (cfg.getBoolean("hide_enchantments", false)) meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        if (cfg.getBoolean("hide_unbreakable", false)) meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        if (cfg.getBoolean("hide_destroys", false)) meta.addItemFlags(ItemFlag.HIDE_DESTROYS);
        if (cfg.getBoolean("hide_placed_on", false)) meta.addItemFlags(ItemFlag.HIDE_PLACED_ON);
        if (cfg.getBoolean("hide_potion_effects", false)) {
            try {
                meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
            } catch (IllegalArgumentException ignored) {

            }
        }
    }
}
