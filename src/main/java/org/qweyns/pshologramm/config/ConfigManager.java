package org.qweyns.pshologramm.config;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.utils.ColorUtil;

import java.util.List;

public class ConfigManager {
    private final PSHologramm plugin;
    private FileConfiguration config;

    public ConfigManager(PSHologramm plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
    }

    public void reload() { plugin.reloadConfig(); this.config = plugin.getConfig(); }
    public FileConfiguration getConfig() { return config; }

    public Material getUpgradeItem() {
        Material mat = Material.matchMaterial(config.getString("settings.upgrade_item", "NETHERITE_INGOT"));
        return mat != null ? mat : Material.NETHERITE_INGOT;
    }

    public int getUpgradeMultiplier() { return config.getInt("settings.upgrade_cost_multiplier", 3); }
    public boolean isMergeEnabled() { return config.getBoolean("settings.merge.enable", false); }
    public long getDamageCooldownTicks() { return config.getLong("settings.damage_cooldown_ticks", 20L); }
    public int getExplosionPenaltyRadius() { return config.getInt("settings.explosion_penalty_radius", 6); }

    public boolean isDamageIndicatorEnabled() { return config.getBoolean("settings.enable_damage_indicator", true); }
    public Component getDamageIndicator() {
        return plugin.getLanguageManager().getMessage("damage_indicator");
    }

    public Component getMessage(String path, String... replacements) {
        return plugin.getLanguageManager().getMessage(path, replacements);
    }

    public String getRawMessage(String path, String... replacements) {
        return plugin.getLanguageManager().getRawMessage(path, replacements);
    }

    public String getEffectTarget(String effect) {
        return config.getString("effect_targets." + effect.toUpperCase(), "MEMBERS").toUpperCase();
    }

    public boolean isExplosionAllowed(String type, String explosionType) {
        String path = "regions." + type + ".explosions." + explosionType;
        if (config.contains(path)) return config.getBoolean(path);
        return config.getBoolean("default_region.explosions." + explosionType, false);
    }

    public String getRegionName(String type) { return config.getString("regions." + type + ".name", config.getString("default_region.name", "&7Обычный приват")); }
    public List<String> getRegionLines(String type) {
        if (config.contains("regions." + type + ".lines")) return config.getStringList("regions." + type + ".lines");
        return config.getStringList("default_region.lines");
    }

    public int getStartDurability(String type) { return config.getInt("regions." + type + ".start_durability", config.getInt("default_region.start_durability", 2)); }
    public int getMaxDurability(String type) { return config.getInt("regions." + type + ".max_durability", config.getInt("default_region.max_durability", 10)); }
    public boolean isSpawnEggAllowed(String type) { return config.getBoolean("regions." + type + ".spawn-egg-use", config.getBoolean("default_region.spawn-egg-use", false)); }
    public boolean isShiftPlaceNormalBlock(String type) { return config.getBoolean("regions." + type + ".shift_to_place_normal_block", config.getBoolean("default_region.shift_to_place_normal_block", false)); }
    public boolean isHologramEnabled(String type) { return config.getBoolean("regions." + type + ".hologram", config.getBoolean("default_region.hologram", true)); }

    public List<String> getAllowedEffects(String type) {
        if (config.contains("regions." + type + ".allowed_effects")) {
            return config.getStringList("regions." + type + ".allowed_effects");
        }
        return config.getStringList("default_region.allowed_effects");
    }

    public String getHologramType(String type) { return config.getString("regions." + type + ".hologram-type", config.getString("default_region.hologram-type", "MODERN")).toUpperCase(); }
    public double getHologramOffset(String type) { return config.getDouble("regions." + type + ".hologram_offset", config.getDouble("default_region.hologram_offset", 2.5)); }
    public int getHologramRange(String type) { return config.getInt("regions." + type + ".hologram_display_range", config.getInt("default_region.hologram_display_range", 5)); }
    public boolean hasShadow(String type) { return config.getBoolean("regions." + type + ".fancyholograms_settings.shadow", config.getBoolean("default_region.fancyholograms_settings.shadow", true)); }
    public float getScale(String type) { return (float) config.getDouble("regions." + type + ".fancyholograms_settings.scale", config.getDouble("default_region.fancyholograms_settings.scale", 1.0)); }
    public String getBillboard(String type) { return config.getString("regions." + type + ".fancyholograms_settings.billboard", config.getString("default_region.fancyholograms_settings.billboard", "CENTER")); }
}
