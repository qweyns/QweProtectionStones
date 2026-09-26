package org.qweyns.qweprotectstones.config;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.qweyns.qweprotectstones.QweProtectStones;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public class ConfigManager {

    private static final List<String> FILES = List.of(
            "config.yml", "protection.yml", "siege.yml", "effects.yml", "visuals.yml", "features.yml", "roles.yml");

    private final QweProtectStones plugin;
    private volatile FileConfiguration config;

    public ConfigManager(QweProtectStones plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        YamlConfiguration merged = new YamlConfiguration();
        YamlConfiguration defaults = new YamlConfiguration();

        for (String name : FILES) {

            if (!new File(plugin.getDataFolder(), name).isFile() && plugin.getResource(name) != null) {
                plugin.saveResource(name, false);
            }
            YamlConfiguration loaded = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name));

            // дефолты из jar, у старых конфигов не хватает новых ключей

            try (InputStream in = plugin.getResource(name)) {
                if (in != null) {
                    YamlConfiguration jar = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(in, StandardCharsets.UTF_8));
                    for (String key : jar.getKeys(false)) {
                        defaults.set(key, jar.get(key));
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось прочитать " + name + " из jar: " + e.getMessage());
            }

            for (String key : loaded.getKeys(false)) {
                if (merged.contains(key)) {
                    plugin.getLogger().warning("Секция '" + key + "' описана больше чем в одном файле конфигурации — значение из " + name + " перезапишет прежнее.");
                }
                merged.set(key, loaded.get(key));
            }
        }

        migrateMovedKeys(merged);
        merged.setDefaults(defaults);
        this.config = merged;
    }

    /**
     * Ключи, переехавшие в другие секции или файлы: новое место → старое.
     * Если у сервера настройка ещё лежит по старому пути, она продолжает работать.
     */
    private static final java.util.Map<String, String> MOVED = java.util.Map.ofEntries(
            java.util.Map.entry("upgrade.item", "settings.upgrade_item"),
            java.util.Map.entry("upgrade.cost_multiplier", "settings.upgrade_cost_multiplier"),
            java.util.Map.entry("upgrade.tax", "settings.upgrade_tax"),
            java.util.Map.entry("menus.command_radius", "settings.menu_command_radius"),
            java.util.Map.entry("menus.custom", "settings.custom_menus"),
            java.util.Map.entry("visuals.preview-messages", "settings.preview-messages"),
            java.util.Map.entry("visuals.damage_indicator.enabled", "siege.damage_indicator"),
            java.util.Map.entry("invites.expire_seconds", "settings.invite_expire_seconds"),
            java.util.Map.entry("invites.sound", "settings.invite_sound"),
            java.util.Map.entry("abandoned", "settings.abandoned"));

    private void migrateMovedKeys(YamlConfiguration merged) {
        for (var entry : MOVED.entrySet()) {
            String now = entry.getKey(), old = entry.getValue();
            if (merged.isSet(now) || !merged.isSet(old)) continue;
            merged.set(now, merged.get(old));
            plugin.getLogger().info("Настройка " + old + " переехала в " + now + " — старое место пока читается.");
        }
    }

    public FileConfiguration getConfig() { return config; }

    public String getAdminPermissionPrefix() {
        String prefix = config.getString("admin.permission-prefix", "qweprotectstones.admin");
        return prefix == null || prefix.isBlank() ? "qweprotectstones.admin" : prefix.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isAdminRequirePerAction() {
        return config.getBoolean("admin.require-per-action", false);
    }

    public int getAdminGiveMaxAmount() {
        return Math.max(1, config.getInt("admin.give.max-amount", 64));
    }

    public double getAdminTeleportOffsetY() {
        return config.getDouble("admin.teleport-offset-y", 1.0);
    }

    public String getCommandName() {
        String name = config.getString("settings.command.name", "region");
        return name == null || name.isBlank() ? "region" : name.toLowerCase(Locale.ROOT);
    }

    public List<String> getCommandAliases() {
        return config.getStringList("settings.command.aliases").stream()
                .filter(alias -> alias != null && !alias.isBlank())
                .map(alias -> alias.toLowerCase(Locale.ROOT))
                .filter(alias -> !alias.equals(getCommandName()))
                .distinct()
                .toList();
    }

    public Material getUpgradeItem() {
        String raw = config.getString("upgrade.item", "NETHERITE_INGOT");
        Material mat = raw == null ? null : Material.matchMaterial(raw);
        if (mat == null || !mat.isItem()) {
            plugin.getLogger().warning("upgrade.item: '" + raw + "' не является предметом, использую NETHERITE_INGOT.");
            return Material.NETHERITE_INGOT;
        }
        return mat;
    }

    public int getUpgradeMultiplier() { return Math.max(1, config.getInt("upgrade.cost_multiplier", 3)); }
    public int getUpgradeTax() { return Math.max(0, config.getInt("upgrade.tax", 0)); }
    public long getDamageCooldownTicks() { return Math.max(0L, config.getLong("siege.damage_cooldown_ticks", 20L)); }
    public int getMenuCommandRadius() { return Math.max(1, config.getInt("menus.command_radius", 6)); }
    public double getExpBoostMultiplier() { return config.getDouble("effects.exp_boost_multiplier", 2.0); }

    public int getExplosionDamageRadius() { return Math.max(0, config.getInt("siege.explosion_damage_radius", 4)); }
    public int getExplosionPenaltyMultiplier() { return Math.max(1, config.getInt("siege.penalty_multiplier", 2)); }

    public long getExplosionPenaltySeconds() {
        long seconds = config.getLong("siege.penalty_time", 300L);
        return seconds > 0 ? seconds : 300L;
    }

    public boolean isDamageIndicatorEnabled() { return config.getBoolean("visuals.damage_indicator.enabled", true); }

    public boolean isSiegeEnabled() { return config.getBoolean("siege.enabled", true); }

    public boolean isCoreBreakDeniedUnderAttack() {
        return config.getBoolean("siege.deny_core_break_under_attack", true);
    }

    public boolean isPenaltyTransferEnabled() {
        return config.getBoolean("settings.transfer-penalty", true);
    }

    public Component getDamageIndicator(int damage) {
        return plugin.getLanguageManager().getMessage("damage_indicator", "%damage%", String.valueOf(damage));
    }

    public Component getMessage(String path, String... replacements) {
        return plugin.getLanguageManager().getMessage(path, replacements);
    }

    public String getRawMessage(String path, String... replacements) {
        return plugin.getLanguageManager().getRawMessage(path, replacements);
    }

    public String getEffectTarget(String effect) {
        String target = config.getString("effect_targets." + effect.toUpperCase(Locale.ROOT), "MEMBERS");
        return target.toUpperCase(Locale.ROOT);
    }

    public List<String> getHologramLines(String typeId, boolean underSiege) {
        if (underSiege
                && regions().getBoolean(typeId, "hologram_under_attack", true)
                && regions().has(typeId, "hologram_lines_under_attack")) {
            List<String> siegeLines = regions().getStringList(typeId, "hologram_lines_under_attack");
            if (!siegeLines.isEmpty()) return siegeLines;
        }
        return regions().getStringList(typeId, "hologram_lines");
    }

    public String getHologramType(String typeId) {
        return regions().getString(typeId, "hologram_provider", "MODERN").toUpperCase(Locale.ROOT);
    }

    public double getHologramOffset(String typeId) { return regions().getDouble(typeId, "hologram_offset", 1.1); }

    public int getHologramRange(String typeId) { return Math.max(1, regions().getInt(typeId, "hologram_display_range", 16)); }

    public boolean hasShadow(String typeId) { return holo(typeId, "shadow", true); }

    public float getScale(String typeId) { return (float) holoDouble(typeId, "scale", 1.0); }

    public String getBillboard(String typeId) {
        return holoString(typeId, "billboard", "CENTER").toUpperCase(Locale.ROOT);
    }

    public int getHologramUpdateInterval(String typeId) {
        return Math.max(1, (int) holoDouble(typeId, "update_interval", 20));
    }

    public boolean isHologramSeeThrough(String typeId) { return holo(typeId, "see_through", false); }

    public String getHologramPermission(String typeId) {
        return holoString(typeId, "permission", "");
    }

    public String getHologramBackground(String typeId) {
        return holoString(typeId, "background", "");
    }

    public String getHologramAlignment(String typeId) {
        return holoString(typeId, "text_alignment", "CENTER").toUpperCase(Locale.ROOT);
    }

    public String getHologramVisibility(String typeId) {
        return holoString(typeId, "visibility", "ALL").toUpperCase(Locale.ROOT);
    }

    public float getHologramShadowRadius(String typeId) { return (float) holoDouble(typeId, "shadow_radius", 0.0); }

    public float getHologramShadowStrength(String typeId) { return (float) holoDouble(typeId, "shadow_strength", 1.0); }

    public int getHologramInterpolation(String typeId) { return (int) holoDouble(typeId, "interpolation_ticks", 0); }

    public float getHologramTranslationX(String typeId) { return (float) holoDouble(typeId, "translation.x", 0.0); }

    public float getHologramTranslationY(String typeId) { return (float) holoDouble(typeId, "translation.y", 0.0); }

    public float getHologramTranslationZ(String typeId) { return (float) holoDouble(typeId, "translation.z", 0.0); }

    public int getHologramBlockLight(String typeId) { return (int) holoDouble(typeId, "brightness.block", -1); }

    public int getHologramSkyLight(String typeId) { return (int) holoDouble(typeId, "brightness.sky", -1); }

    public int getHologramUpdateRange(String typeId) {
        return Math.max(1, (int) holoDouble(typeId, "update_range", 48));
    }

    public boolean isHologramDownOrigin(String typeId) { return holo(typeId, "down_origin", false); }

    private RegionConfig regions() {
        return plugin.getRegionConfig();
    }

    private String holoKey(String typeId, String key) {
        String modern = "hologram_settings." + key;
        String legacy = "fancyholograms_settings." + key;
        if (regions().raw().isSet("region_types." + typeId + "." + modern)) return modern;
        if (regions().raw().isSet("region_types." + typeId + "." + legacy)) return legacy;
        return regions().has(typeId, modern) ? modern : legacy;
    }

    private boolean holo(String typeId, String key, boolean fallback) {
        return regions().getBoolean(typeId, holoKey(typeId, key), fallback);
    }

    private double holoDouble(String typeId, String key, double fallback) {
        return regions().getDouble(typeId, holoKey(typeId, key), fallback);
    }

    private String holoString(String typeId, String key, String fallback) {
        String value = regions().getString(typeId, holoKey(typeId, key), fallback);
        return value == null ? fallback : value;
    }

    public String getEffectPath(String typeId, String action) {
        return regions().sectionPath(typeId, action);
    }
}
