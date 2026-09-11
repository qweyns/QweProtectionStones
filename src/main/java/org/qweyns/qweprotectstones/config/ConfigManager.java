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

    /**
     * Настройки разложены по темам, чтобы главный файл не разрастался:
     * config.yml — ядро, protection.yml — защита, siege.yml — осады,
     * effects.yml — эффекты, visuals.yml — звуки и карта,
     * features.yml — рынок, бэкапы и прочие функции.
     *
     * <p>Для чтения всё собирается в одну конфигурацию: пути ключей
     * не менялись, поэтому в коде обращение выглядит как раньше.</p>
     */
    private static final List<String> FILES = List.of(
            "config.yml", "protection.yml", "siege.yml", "effects.yml", "visuals.yml", "features.yml");

    private final QweProtectStones plugin;
    private FileConfiguration config;

    public ConfigManager(QweProtectStones plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        YamlConfiguration merged = new YamlConfiguration();
        YamlConfiguration defaults = new YamlConfiguration();

        for (String name : FILES) {
            // Файл создаётся из jar при первом запуске.
            if (!new File(plugin.getDataFolder(), name).isFile() && plugin.getResource(name) != null) {
                plugin.saveResource(name, false);
            }
            YamlConfiguration loaded = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name));

            // Дефолты из jar: у обновившихся серверов в старом файле может
            // не хватать новых ключей — значения возьмутся отсюда.
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

        merged.setDefaults(defaults);
        this.config = merged;
    }

    public FileConfiguration getConfig() { return config; }

    // ------------------------------------------------------------------
    // Административная команда
    // ------------------------------------------------------------------

    /** База прав для подкоманд /qps: правой каждого действия будет <префикс>.<действие>. */
    public String getAdminPermissionPrefix() {
        String prefix = config.getString("admin.permission-prefix", "qweprotectstones.admin");
        return prefix == null || prefix.isBlank() ? "qweprotectstones.admin" : prefix.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Требовать ли отдельное право <префикс>.<действие> даже при наличии общего
     * <префикс>. false — общего права достаточно для любой подкоманды.
     */
    public boolean isAdminRequirePerAction() {
        return config.getBoolean("admin.require-per-action", false);
    }

    /** Максимум блоков привата за одну выдачу (/qps give). */
    public int getAdminGiveMaxAmount() {
        return Math.max(1, config.getInt("admin.give.max-amount", 64));
    }

    /** На сколько блоков выше ядра ставится игрок при /qps tp. */
    public double getAdminTeleportOffsetY() {
        return config.getDouble("admin.teleport-offset-y", 1.0);
    }

    // ------------------------------------------------------------------
    // Команда
    // ------------------------------------------------------------------

    public String getCommandName() {
        String name = config.getString("settings.command.name", "region");
        return name == null || name.isBlank() ? "region" : name.toLowerCase(Locale.ROOT);
    }

    /** Дубликаты отсеиваются: повторный алиас в списке ломал бы регистрацию команды. */
    public List<String> getCommandAliases() {
        return config.getStringList("settings.command.aliases").stream()
                .filter(alias -> alias != null && !alias.isBlank())
                .map(alias -> alias.toLowerCase(Locale.ROOT))
                .filter(alias -> !alias.equals(getCommandName()))
                .distinct()
                .toList();
    }

    // ------------------------------------------------------------------
    // Общие настройки
    // ------------------------------------------------------------------

    /** Ставить блок-ядро с зажатым Shift как обычный блок, без создания привата. */
    public boolean isSneakPlacesPlainBlock() {
        return config.getBoolean("settings.sneak_places_plain_block", true);
    }

    public Material getUpgradeItem() {
        String raw = config.getString("settings.upgrade_item", "NETHERITE_INGOT");
        Material mat = raw == null ? null : Material.matchMaterial(raw);
        if (mat == null || !mat.isItem()) {
            plugin.getLogger().warning("settings.upgrade_item: '" + raw + "' не является предметом, использую NETHERITE_INGOT.");
            return Material.NETHERITE_INGOT;
        }
        return mat;
    }

    public int getUpgradeMultiplier() { return Math.max(1, config.getInt("settings.upgrade_cost_multiplier", 3)); }
    public int getUpgradeTax() { return Math.max(0, config.getInt("settings.upgrade_tax", 0)); }
    public long getDamageCooldownTicks() { return Math.max(0L, config.getLong("siege.damage_cooldown_ticks", 20L)); }
    public int getMenuCommandRadius() { return Math.max(1, config.getInt("settings.menu_command_radius", 6)); }
    public double getExpBoostMultiplier() { return config.getDouble("effects.exp_boost_multiplier", 2.0); }

    public int getExplosionDamageRadius() { return Math.max(0, config.getInt("siege.explosion_damage_radius", 4)); }
    public int getExplosionPenaltyMultiplier() { return Math.max(1, config.getInt("siege.penalty_multiplier", 2)); }

    /** Длительность штрафа в секундах. */
    public long getExplosionPenaltySeconds() {
        long seconds = config.getLong("siege.penalty_time", 300L);
        return seconds > 0 ? seconds : 300L;
    }

    public boolean isDamageIndicatorEnabled() { return config.getBoolean("siege.damage_indicator", true); }

    /** Мастер-выключатель осад: могут ли взрывы снимать прочность ядра (siege.yml). */
    public boolean isSiegeEnabled() { return config.getBoolean("siege.enabled", true); }

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

    // ------------------------------------------------------------------
    // Оформление конкретного типа привата (regions.yml, с откатом на default_region)
    // ------------------------------------------------------------------

    /**
     * Строки голограммы. Во время осады показываются
     * {@code hologram_lines_under_attack}, если они заданы, — так «под атакой»
     * видно прямо над ядром, а не только в меню.
     */
    public List<String> getHologramLines(String typeId, boolean underSiege) {
        if (underSiege && regions().has(typeId, "hologram_lines_under_attack")) {
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

    // --- общие для обоих провайдеров ---

    public boolean hasShadow(String typeId) { return holo(typeId, "shadow", true); }

    public float getScale(String typeId) { return (float) holoDouble(typeId, "scale", 1.0); }

    public String getBillboard(String typeId) {
        return holoString(typeId, "billboard", "CENTER").toUpperCase(Locale.ROOT);
    }

    /** Как часто голограмма перерисовывается, в тиках. */
    public int getHologramUpdateInterval(String typeId) {
        return Math.max(1, (int) holoDouble(typeId, "update_interval", 20));
    }

    /** Видно ли текст сквозь блоки. */
    public boolean isHologramSeeThrough(String typeId) { return holo(typeId, "see_through", false); }

    /** Право, без которого голограмма не показывается; пусто — показывать всем. */
    public String getHologramPermission(String typeId) {
        return holoString(typeId, "permission", "");
    }

    // --- только FancyHolograms ---

    /** Цвет подложки текста в формате {@code #RRGGBB}; пусто — без подложки. */
    public String getHologramBackground(String typeId) {
        return holoString(typeId, "background", "");
    }

    /** Выравнивание текста: LEFT, CENTER или RIGHT. */
    public String getHologramAlignment(String typeId) {
        return holoString(typeId, "text_alignment", "CENTER").toUpperCase(Locale.ROOT);
    }

    /** Кому видна голограмма: ALL, PERMISSION_REQUIRED или MANUAL. */
    public String getHologramVisibility(String typeId) {
        return holoString(typeId, "visibility", "ALL").toUpperCase(Locale.ROOT);
    }

    public float getHologramShadowRadius(String typeId) { return (float) holoDouble(typeId, "shadow_radius", 0.0); }

    public float getHologramShadowStrength(String typeId) { return (float) holoDouble(typeId, "shadow_strength", 1.0); }

    public int getHologramInterpolation(String typeId) { return (int) holoDouble(typeId, "interpolation_ticks", 0); }

    /** Дополнительное смещение текста относительно ядра. */
    public float getHologramTranslationX(String typeId) { return (float) holoDouble(typeId, "translation.x", 0.0); }

    public float getHologramTranslationY(String typeId) { return (float) holoDouble(typeId, "translation.y", 0.0); }

    public float getHologramTranslationZ(String typeId) { return (float) holoDouble(typeId, "translation.z", 0.0); }

    /** Яркость от 0 до 15; отрицательное значение — брать освещение сцены. */
    public int getHologramBlockLight(String typeId) { return (int) holoDouble(typeId, "brightness.block", -1); }

    public int getHologramSkyLight(String typeId) { return (int) holoDouble(typeId, "brightness.sky", -1); }

    // --- только DecentHolograms ---

    /** Радиус, в котором DecentHolograms обновляет содержимое. */
    public int getHologramUpdateRange(String typeId) {
        return Math.max(1, (int) holoDouble(typeId, "update_range", 48));
    }

    /** Считать заданную точку низом голограммы, а не верхом. */
    public boolean isHologramDownOrigin(String typeId) { return holo(typeId, "down_origin", false); }

    // ------------------------------------------------------------------
    // Чтение секции hologram_settings
    // ------------------------------------------------------------------

    private RegionConfig regions() {
        return plugin.getRegionConfig();
    }

    /**
     * Ключи голограмм читаются из {@code hologram_settings}, а при их отсутствии —
     * из прежней секции {@code fancyholograms_settings}: конфиги, написанные до
     * появления общих настроек, продолжают работать.
     */
    private String holoKey(String typeId, String key) {
        String modern = "hologram_settings." + key;
        return regions().has(typeId, modern) ? modern : "fancyholograms_settings." + key;
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

    /** Путь к секции звука/молнии для действия create, damage или remove. */
    public String getEffectPath(String typeId, String action) {
        return regions().sectionPath(typeId, action);
    }
}
