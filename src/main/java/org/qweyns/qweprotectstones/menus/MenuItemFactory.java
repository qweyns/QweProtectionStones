package org.qweyns.qweprotectstones.menus;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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

/**
 * Сборка предметов меню из YAML.
 *
 * <p>Статичные предметы (в конфиге которых нет ни одного плейсхолдера)
 * собираются один раз и переиспользуются: при {@code update_interval: 10} меню
 * из 45 слотов иначе пересобиралось бы целиком дважды в секунду, каждый раз
 * заново разбирая цвета и MiniMessage.</p>
 */
public class MenuItemFactory {

    private final QweProtectStones plugin;
    private final MenuPlaceholders placeholders;

    /** Кэш статичных предметов: ключ — меню и имя элемента. */
    private final Map<String, ItemStack> staticCache = new ConcurrentHashMap<>();

    public MenuItemFactory(QweProtectStones plugin, MenuPlaceholders placeholders) {
        this.plugin = plugin;
        this.placeholders = placeholders;
    }

    /** Сбрасывается при перезагрузке меню — иначе останутся предметы из старого конфига. */
    public void clearCache() {
        staticCache.clear();
    }

    public ItemStack build(ConfigurationSection cfg, Player player, Region region, Map<String, String> extra) {
        return build(cfg, player, region, extra, null);
    }

    /**
     * @param cacheKey ключ кэша или {@code null}, если предмет кэшировать нельзя
     */
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

    /** Предмет статичен, если ни в имени, ни в лоре, ни в материале нет плейсхолдеров. */
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
        if (cfg.contains("custom_model_data")) meta.setCustomModelData(cfg.getInt("custom_model_data"));

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
            // fromString сам подставит пространство имён minecraft и вернёт null вместо исключения.
            NamespacedKey key = NamespacedKey.fromString(parts[0].toLowerCase(Locale.ROOT));
            if (key == null) {
                plugin.getLogger().warning("Некорректное имя зачарования в меню: " + parts[0]);
                continue;
            }

            Enchantment enchantment = Registry.ENCHANTMENT.get(key);
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
                // Флага нет на старых версиях API — просто пропускаем.
            }
        }
    }
}
