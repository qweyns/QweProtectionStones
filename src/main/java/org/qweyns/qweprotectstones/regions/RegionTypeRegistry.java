package org.qweyns.qweprotectstones.regions;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.qweyns.qweprotectstones.QweProtectStones;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Разбирает секцию {@code region_types} из config.yml. Каждый тип описывается
 * материалом блока-ядра; поиск по материалу выполняется на каждой установке
 * блока, поэтому держим отдельную карту Material -> тип.
 */
public class RegionTypeRegistry {

    private static final List<String> EXPLOSION_KINDS = List.of("TNT", "CREEPER", "WITHER", "ENDER_CRYSTAL", "BED");

    private final QweProtectStones plugin;
    private final Map<String, RegionType> byId = new LinkedHashMap<>();
    /** Тот же реестр, но ключи в нижнем регистре — для поиска без учёта регистра. */
    private final Map<String, RegionType> byIdNormalized = new LinkedHashMap<>();
    private final Map<Material, RegionType> byMaterial = new EnumMap<>(Material.class);

    public RegionTypeRegistry(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public void load() {
        byId.clear();
        byIdNormalized.clear();
        byMaterial.clear();

        ConfigurationSection defaults = plugin.getRegionConfig().defaults();
        ConfigurationSection types = plugin.getRegionConfig().types();

        if (types == null) {
            plugin.getLogger().severe("В regions.yml нет секции region_types — приваты создавать будет нечем!");
            return;
        }

        for (String key : types.getKeys(false)) {
            ConfigurationSection section = types.getConfigurationSection(key);
            if (section == null) continue;

            Material material = Material.matchMaterial(key);
            if (material == null || !material.isBlock()) {
                plugin.getLogger().warning("region_types." + key + ": '" + key + "' не является блоком — тип пропущен.");
                continue;
            }
            if (byMaterial.containsKey(material)) {
                plugin.getLogger().warning("region_types." + key + ": материал уже занят другим типом — тип пропущен.");
                continue;
            }

            RegionType type = parse(key, material, section, defaults);
            byId.put(type.id(), type);
            byIdNormalized.put(type.id().toLowerCase(Locale.ROOT), type);
            byMaterial.put(material, type);
        }

        if (byId.isEmpty()) {
            plugin.getLogger().severe("Не загружено ни одного типа привата — проверьте секцию region_types в regions.yml.");
        } else {
            plugin.getLogger().info("Загружено типов приватов: " + byId.size() + " (" + String.join(", ", byId.keySet()) + ")");
        }
    }

    private RegionType parse(String id, Material material, ConfigurationSection section, ConfigurationSection defaults) {
        int radiusX = readInt(section, defaults, "radius_x", readInt(section, defaults, "radius", 16));
        int radiusZ = readInt(section, defaults, "radius_z", readInt(section, defaults, "radius", 16));
        int radiusY = readInt(section, defaults, "radius_y", 64);

        int startDurability = readInt(section, defaults, "start_durability", 2);
        int maxDurability = readInt(section, defaults, "max_durability", 10);

        Set<String> worlds = new HashSet<>();
        for (String world : readStringList(section, defaults, "worlds")) {
            worlds.add(world.toLowerCase(Locale.ROOT));
        }

        return new RegionType(
                id,
                material,
                readString(section, defaults, "display_name", "&f" + id),
                radiusX, radiusY, radiusZ,
                startDurability,
                maxDurability,
                readBoolean(section, defaults, "enable_durability_upgrade", true),
                readInt(section, defaults, "max_per_player", 0),
                readInt(section, defaults, "min_distance_to_others", 0),
                readString(section, defaults, "place_permission", ""),
                worlds,
                readString(section, defaults, "worlds_mode", "whitelist").equalsIgnoreCase("blacklist"),
                readStringList(section, defaults, "allowed_effects"),
                parseFlags(section, defaults, id),
                parseExplosions(section, defaults),
                readBoolean(section, defaults, "spawn-egg-use", false),
                readBoolean(section, defaults, "hologram", true),
                readBoolean(section, defaults, "return_block_on_remove", true),

                readInt(section, defaults, "damage_cooldown_ticks", -1),
                readInt(section, defaults, "explosion_damage_radius", -1),
                readBoolean(section, defaults, "raid_immune", false),
                readString(section, defaults, "upgrade.item", ""),
                readInt(section, defaults, "upgrade.cost_multiplier", -1),
                readInt(section, defaults, "upgrade.tax", -1),
                readString(section, defaults, "border_color", ""),
                readString(section, defaults, "menu", ""),
                readString(section, defaults, "greeting", ""),
                readString(section, defaults, "farewell", ""),
                readBoolean(section, defaults, "full_height", false),

                readBoolean(section, defaults, "restrict-obtaining", false),
                readString(section, defaults, "item.name", ""),
                readStringList(section, defaults, "item.lore"),
                readBoolean(section, defaults, "item.glow", false),
                parseRecipe(section, defaults, id));
    }

    /**
     * Собственный крафт блока-ядра. Отсутствие секции recipe — это норма:
     * блок получается как обычный блок Minecraft.
     */
    private CoreRecipe parseRecipe(ConfigurationSection section, ConfigurationSection defaults, String typeId) {
        ConfigurationSection recipe = section.contains("recipe")
                ? section.getConfigurationSection("recipe")
                : (defaults != null ? defaults.getConfigurationSection("recipe") : null);
        if (recipe == null) return null;

        List<String> pattern = recipe.getStringList("pattern");
        if (pattern.isEmpty() || pattern.size() > 3) {
            plugin.getLogger().warning("recipe у " + typeId + ": схема должна быть 1–3 строки по 1–3 символа — рецепт пропущен.");
            return null;
        }
        int width = pattern.get(0).length();
        for (String row : pattern) {
            if (row.length() != width || row.isEmpty() || row.length() > 3) {
                plugin.getLogger().warning("recipe у " + typeId + ": строки схемы разной длины — рецепт пропущен.");
                return null;
            }
        }

        Map<Character, Material> ingredients = new java.util.LinkedHashMap<>();
        ConfigurationSection ingredientSection = recipe.getConfigurationSection("ingredients");
        if (ingredientSection == null) {
            plugin.getLogger().warning("recipe у " + typeId + ": нет секции ingredients — рецепт пропущен.");
            return null;
        }
        for (String key : ingredientSection.getKeys(false)) {
            if (key.length() != 1 || key.charAt(0) == ' ') {
                plugin.getLogger().warning("recipe у " + typeId + ": символ ингредиента '" + key + "' должен быть одной буквой — пропущен.");
                continue;
            }
            Material material = Material.matchMaterial(ingredientSection.getString(key, ""));
            if (material == null || !material.isItem()) {
                plugin.getLogger().warning("recipe у " + typeId + ": '" + ingredientSection.getString(key) + "' не является предметом — пропущен.");
                continue;
            }
            ingredients.put(key.charAt(0), material);
        }

        // Каждая буква схемы должна быть известна, иначе крафт не зарегистрируется.
        for (char c : String.join("", pattern).toCharArray()) {
            if (c != ' ' && !ingredients.containsKey(c)) {
                plugin.getLogger().warning("recipe у " + typeId + ": символ '" + c + "' не описан в ingredients — рецепт пропущен.");
                return null;
            }
        }

        return new CoreRecipe(pattern, ingredients, recipe.getInt("result_amount", 1));
    }

    private Map<RegionFlag, Boolean> parseFlags(ConfigurationSection section, ConfigurationSection defaults, String typeId) {
        Map<RegionFlag, Boolean> flags = new EnumMap<>(RegionFlag.class);

        applyFlags(defaults, flags, typeId);
        applyFlags(section, flags, typeId);
        return flags;
    }

    private void applyFlags(ConfigurationSection owner, Map<RegionFlag, Boolean> target, String typeId) {
        if (owner == null) return;

        ConfigurationSection flagSection = owner.getConfigurationSection("flags");
        if (flagSection == null) return;

        for (String key : flagSection.getKeys(false)) {
            RegionFlag.parse(key).ifPresentOrElse(
                    flag -> target.put(flag, flagSection.getBoolean(key)),
                    () -> plugin.getLogger().warning("Неизвестный флаг '" + key + "' у типа " + typeId + " — пропущен."));
        }
    }

    private Map<String, Boolean> parseExplosions(ConfigurationSection section, ConfigurationSection defaults) {
        Map<String, Boolean> rules = new HashMap<>();

        for (ConfigurationSection owner : new ConfigurationSection[]{defaults, section}) {
            if (owner == null) continue;
            ConfigurationSection explosions = owner.getConfigurationSection("explosions");
            if (explosions == null) continue;

            for (String kind : EXPLOSION_KINDS) {
                if (explosions.contains(kind)) rules.put(kind, explosions.getBoolean(kind));
            }
        }
        return rules;
    }

    // ------------------------------------------------------------------
    // Чтение с откатом на default_region
    // ------------------------------------------------------------------

    private int readInt(ConfigurationSection section, ConfigurationSection defaults, String key, int fallback) {
        if (section.contains(key)) return section.getInt(key);
        if (defaults != null && defaults.contains(key)) return defaults.getInt(key);
        return fallback;
    }

    private boolean readBoolean(ConfigurationSection section, ConfigurationSection defaults, String key, boolean fallback) {
        if (section.contains(key)) return section.getBoolean(key);
        if (defaults != null && defaults.contains(key)) return defaults.getBoolean(key);
        return fallback;
    }

    private String readString(ConfigurationSection section, ConfigurationSection defaults, String key, String fallback) {
        if (section.contains(key)) return section.getString(key, fallback);
        if (defaults != null && defaults.contains(key)) return defaults.getString(key, fallback);
        return fallback;
    }

    private List<String> readStringList(ConfigurationSection section, ConfigurationSection defaults, String key) {
        if (section.contains(key)) return section.getStringList(key);
        if (defaults != null && defaults.contains(key)) return defaults.getStringList(key);
        return new ArrayList<>();
    }

    // ------------------------------------------------------------------
    // Доступ
    // ------------------------------------------------------------------

    public RegionType byId(String id) {
        if (id == null) return null;
        RegionType type = byId.get(id);
        // Регистр не должен мешать: /qps give пишет тип в нижнем регистре,
        // а ключи в regions.yml традиционно заглавные (DIAMOND_BLOCK).
        return type != null ? type : byIdNormalized.get(id.toLowerCase(Locale.ROOT));
    }

    public RegionType byMaterial(Material material) {
        return material == null ? null : byMaterial.get(material);
    }

    public boolean isRegionBlock(Material material) {
        return material != null && byMaterial.containsKey(material);
    }

    public Collection<RegionType> all() {
        return byId.values();
    }

    public Set<String> ids() {
        return byId.keySet();
    }

    /**
     * Тип для привата, загруженного из базы. Если тип удалили из конфига,
     * подставляем любой доступный, чтобы приват не потерял защиту целиком.
     */
    public RegionType resolveOrFallback(String typeId) {
        RegionType type = byId(typeId);
        if (type != null) return type;

        RegionType fallback = byId.values().stream().findFirst().orElse(null);
        if (fallback != null) {
            plugin.getLogger().warning("Тип привата '" + typeId + "' отсутствует в regions.yml — использую '" + fallback.id() + "'.");
        }
        return fallback;
    }
}
