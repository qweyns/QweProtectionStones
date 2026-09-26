package org.qweyns.qweprotectstones.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Tunables {

    private final QweProtectStones plugin;

    private long menuClickCooldownMs;
    private long deleteConfirmMs;
    private long denyMessageCooldownMs;
    private long moveThrottleMs;
    private int effectRefreshTicks;
    private int effectDurationTicks;
    private int maxAutoAddFriends;
    private long dbFlushTicks;
    private int dbBatchSize;
    private int logPageSize;
    private int logMaxPageSize;
    private int findSpotMaxRings;
    private long animationPeriodTicks;
    private long siegeWindowMs;

    private SoundSetting menuDenied;
    private SoundSetting menuSuccess;
    private SoundSetting raidAttack;
    private SoundSetting raidDestroyed;
    private SoundSetting raidNearby;
    private SoundSetting intruderAlert;
    private SoundSetting inviteReceived;

    private ParticleSetting particles;

    private int helpPageSize;
    private int adminHelpPageSize;

    private boolean hoppersEnabled;
    private boolean hoppersBlockOutflow;
    private boolean hoppersBlockInflow;
    private boolean pistonsCanMoveCore;
    private boolean borderFrostWalker;
    private boolean borderMobTrails;
    private boolean borderBonemeal;
    private boolean borderFishing;
    private boolean borderDispensers;
    private InteractRules interactRules;
    private List<String> borderDispenserItems;
    private boolean regionEnterEnabled;
    private boolean regionLeaveEnabled;
    private boolean homeCancelOnMove;
    private boolean homeCancelOnDamage;
    private boolean previewMessages;
    private String regionEnterChannel;
    private String regionLeaveChannel;

    private Set<RegionFlag> lockedFlags;

    /**
     * Действия, которые роль может разрешать. Список ролей и их действий — roles.yml.
     * Ключ действия пишется в конфиге строчными буквами через дефис.
     */
    public enum TrustAction {
        INTERACT("interact"),          // двери, кнопки, рычаги, кровати, жители
        CONTAINER("container"),        // сундуки, бочки, печи, вагонетки с сундуком
        BUILD("build"),                // ставить/ломать блоки, инструменты по блокам
        ENTITY("entity"),              // рамки, стойки брони, картины, мобы, транспорт
        MENU("menu"),                  // открыть меню привата (клик по ядру, /ps menu)
        MANAGE("manage"),              // журнал и прочие функции управляющего
        FLAGS("flags"),                // менять флаги
        MEMBERS("members"),            // выдавать/отзывать роли, приглашать
        BAN("ban"),                    // банить и разбанивать
        RENAME("rename"),              // название и оформление
        UPGRADE("upgrade"),            // улучшения прочности и покупка эффектов
        HOME("home"),                  // /ps home к этому привату
        VIEW_MEMBERS("view-members"),  // список участников
        GLOW("glow"),                  // подсветка границ
        EFFECTS("effects"),            // получать эффекты привата и бонусы опыта
        ALERTS("alerts"),              // уведомления об атаке
        ENTRY("entry");                // входить и телепортироваться, даже если флаги запрещают

        private final String key;

        TrustAction(String key) { this.key = key; }

        public String key() { return key; }

        public static java.util.Optional<TrustAction> parse(String raw) {
            if (raw == null) return java.util.Optional.empty();
            String needle = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            for (TrustAction action : values()) {
                if (action.key.equals(needle)) return java.util.Optional.of(action);
            }
            return java.util.Optional.empty();
        }
    }

    public Tunables(QweProtectStones plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration cfg = plugin.getConfigManager().getConfig();

        menuClickCooldownMs = positive(cfg.getLong("timings.menu_click_cooldown_ms", 300L), 300L);
        deleteConfirmMs = positive(cfg.getLong("timings.delete_confirm_seconds", 30L), 30L) * 1000L;
        denyMessageCooldownMs = positive(cfg.getLong("timings.deny_message_cooldown_ms", 2000L), 2000L);
        moveThrottleMs = positive(cfg.getLong("timings.move_throttle_ms", 300L), 300L);
        effectRefreshTicks = (int) positive(cfg.getLong("timings.effect_refresh_ticks", 40L), 40L);
        effectDurationTicks = (int) positive(cfg.getLong("timings.effect_duration_ticks", 60L), 60L);
        dbFlushTicks = positive(cfg.getLong("timings.db_flush_ticks", 60L), 60L);
        animationPeriodTicks = positive(cfg.getLong("timings.animation_period_ticks", 10L), 10L);

        siegeWindowMs = positive(cfg.getLong("siege.window_seconds", 300L), 300L) * 1000L;

        maxAutoAddFriends = (int) bounded(cfg.getLong("limits.autoadd_friends", 50L), 1, 500);
        dbBatchSize = (int) bounded(cfg.getLong("limits.db_batch_size", 500L), 25, 10_000);
        logPageSize = (int) bounded(cfg.getLong("limits.log_page_size", 15L), 1, 100);
        logMaxPageSize = (int) bounded(cfg.getLong("limits.log_max_page_size", 50L), logPageSize, 500);
        findSpotMaxRings = (int) bounded(cfg.getLong("limits.findspot_rings", 24L), 1, 200);

        // эффект живёт дольше периода обновления, иначе мигает

        if (effectDurationTicks <= effectRefreshTicks) {
            effectDurationTicks = effectRefreshTicks + 20;
        }

        loadSounds(cfg);
        loadParticles(cfg);
        loadTrust(cfg);
        validateEffectTargets(cfg);
    }

    private void validateEffectTargets(FileConfiguration cfg) {
        ConfigurationSection section = cfg.getConfigurationSection("effect_targets");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String value = section.getString(key, "");
            String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);

            if (!normalized.equals("MEMBERS") && !normalized.equals("ENEMIES")) {
                plugin.getLogger().warning("effect_targets." + key + ": ожидается MEMBERS или ENEMIES, а указано '"
                        + value + "' — эффект не будет накладываться никому.");
            }
        }
    }

    private void loadSounds(FileConfiguration cfg) {
        // Звуки лежат рядом со своими механиками; старые ключи sounds.* читаются как запасной вариант.
        // Звуки меню прокачки — в menus/upgrade.yml (sounds.success / sounds.denied), здесь только их значения по умолчанию.
        menuDenied = sound(cfg, "sounds.menu_denied", "sounds.menu_denied", "ENTITY_VILLAGER_NO:1.0:1.0");
        menuSuccess = sound(cfg, "sounds.menu_success", "sounds.menu_success", "BLOCK_ANVIL_USE:1.0:1.0");
        raidAttack = sound(cfg, "notifications.sounds.attack", "sounds.raid_attack", "ENTITY_ENDER_DRAGON_GROWL:1.0:1.0");
        raidDestroyed = sound(cfg, "notifications.sounds.destroyed", "sounds.raid_destroyed", "ENTITY_WITHER_DEATH:1.0:1.0");
        intruderAlert = sound(cfg, "notifications.sounds.intruder", "sounds.intruder_alert", "BLOCK_NOTE_BLOCK_BELL:1.0:1.0");
        raidNearby = sound(cfg, "siege.neighbour_alert_sound", "sounds.raid_nearby", "ENTITY_WITHER_SHOOT:0.4:0.7");
        inviteReceived = sound(cfg, "invites.sound", "sounds.invite_received", "ENTITY_EXPERIENCE_ORB_PICKUP:1.0:1.2");
    }

    private SoundSetting sound(FileConfiguration cfg, String path, String legacyPath, String defaultValue) {
        SoundSetting fallback = SoundSetting.parse(defaultValue, SoundSetting.NONE);
        String key = cfg.isSet(path) ? path : legacyPath;
        String raw = cfg.getString(key);

        SoundSetting parsed = SoundSetting.parse(raw, null);
        if (parsed == null) {

            if (raw != null && !raw.isBlank()) {
                plugin.getLogger().warning(key + ": неизвестный звук '" + raw
                        + "', использую " + defaultValue);
            }
            return fallback;
        }
        return parsed;
    }

    private void loadParticles(FileConfiguration cfg) {
        particles = ParticleSetting.of(
                cfg.getString("visuals.particle.type", "DUST"),
                cfg.getDouble("visuals.particle.size", 1.5),
                cfg.getDouble("visuals.particle.density", 1.0),
                cfg.getInt("visuals.particle.max_points", 2_000),
                bad -> plugin.getLogger().warning("visuals.particle.type: неизвестная частица '" + bad + "', использую DUST"));
    }

    private void loadTrust(FileConfiguration cfg) {
        TrustLevel.install(RoleLoader.load(cfg, plugin.getLogger()));

        Set<RegionFlag> locked = EnumSet.noneOf(RegionFlag.class);

        helpPageSize = Math.max(1, cfg.getInt("help.page-size", 8));
        adminHelpPageSize = Math.max(1, cfg.getInt("help.admin-page-size", 10));

        // InventoryMoveItemEvent жарит сотни раз в секунду, ключи читаем один раз

        hoppersEnabled = cfg.getBoolean("protection.hoppers.enable", true);
        hoppersBlockOutflow = cfg.getBoolean("protection.hoppers.block-outflow", true);
        hoppersBlockInflow = cfg.getBoolean("protection.hoppers.block-inflow", false);
        pistonsCanMoveCore = cfg.getBoolean("protection.pistons.move-core", false);
        borderFrostWalker = cfg.getBoolean("protection.border.frost-walker", true);
        borderMobTrails = cfg.getBoolean("protection.border.mob-trails", true);
        borderBonemeal = cfg.getBoolean("protection.border.bonemeal", true);
        borderFishing = cfg.getBoolean("protection.border.fishing", true);
        borderDispensers = cfg.getBoolean("protection.border.dispensers.enable", true);
        interactRules = InteractRules.load(cfg, plugin.getLogger());
        borderDispenserItems = cfg.getStringList("protection.border.dispensers.items").stream()
                .map(value -> value.trim().toUpperCase(Locale.ROOT)).filter(value -> !value.isEmpty()).toList();
        // Сообщения входа/выхода всегда идут в чат; выключаются флагом greeting или пустым текстом в lang.
        regionEnterEnabled = true;
        regionLeaveEnabled = true;
        homeCancelOnMove = cfg.getBoolean("home.cancel-on-move", true);
        homeCancelOnDamage = cfg.getBoolean("home.cancel-on-damage", true);
        previewMessages = cfg.getBoolean("visuals.preview-messages", false);
        // канал ACTIONBAR выпилен: старые конфиги читаем как CHAT
        regionEnterChannel = "CHAT";
        regionLeaveChannel = "CHAT";

        List<String> rawLocked = cfg.getStringList("flags.locked");
        for (String raw : rawLocked) {
            RegionFlag flag = RegionFlag.parse(raw).orElse(null);
            if (flag == null) plugin.getLogger().warning("flags.locked: неизвестный флаг '" + raw + "'");
            else locked.add(flag);
        }
        lockedFlags = locked.isEmpty() ? EnumSet.noneOf(RegionFlag.class) : EnumSet.copyOf(locked);
    }

    private static long positive(long value, long fallback) {
        return value > 0 ? value : fallback;
    }

    private static long bounded(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public long menuClickCooldownMs() { return menuClickCooldownMs; }
    public long deleteConfirmMs() { return deleteConfirmMs; }
    public long denyMessageCooldownMs() { return denyMessageCooldownMs; }
    public long moveThrottleMs() { return moveThrottleMs; }
    public int effectRefreshTicks() { return effectRefreshTicks; }
    public int effectDurationTicks() { return effectDurationTicks; }
    public int maxAutoAddFriends() { return maxAutoAddFriends; }
    public long dbFlushTicks() { return dbFlushTicks; }
    public int dbBatchSize() { return dbBatchSize; }
    public int logPageSize() { return logPageSize; }
    public int logMaxPageSize() { return logMaxPageSize; }
    public int findSpotMaxRings() { return findSpotMaxRings; }
    public long animationPeriodTicks() { return animationPeriodTicks; }
    public long siegeWindowMs() { return siegeWindowMs; }

    public SoundSetting menuDenied() { return menuDenied; }
    public SoundSetting menuSuccess() { return menuSuccess; }
    public SoundSetting raidAttack() { return raidAttack; }
    public SoundSetting raidDestroyed() { return raidDestroyed; }
    public SoundSetting raidNearby() { return raidNearby; }

    public int helpPageSize() { return helpPageSize; }
    public int adminHelpPageSize() { return adminHelpPageSize; }

    public boolean hoppersEnabled() { return hoppersEnabled; }

    public boolean pistonsCanMoveCore() { return pistonsCanMoveCore; }
    public boolean hoppersBlockOutflow() { return hoppersBlockOutflow; }
    public boolean hoppersBlockInflow() { return hoppersBlockInflow; }
    public boolean borderFrostWalker() { return borderFrostWalker; }
    public boolean borderMobTrails() { return borderMobTrails; }
    public boolean borderBonemeal() { return borderBonemeal; }
    public boolean borderFishing() { return borderFishing; }
    public boolean borderDispensers() { return borderDispensers; }
    public InteractRules interactRules() { return interactRules; }

    /** Попадает ли предмет раздатчика под запрет: "*" — все, "*_BUCKET" — по окончанию, "WATER*" — по началу. */
    public boolean dispenserItemBlocked(org.bukkit.Material material) {
        String name = material.name();
        for (String pattern : borderDispenserItems) {
            if (pattern.equals("*")) return true;
            if (pattern.startsWith("*") && name.endsWith(pattern.substring(1))) return true;
            if (pattern.endsWith("*") && name.startsWith(pattern.substring(0, pattern.length() - 1))) return true;
            if (pattern.equals(name)) return true;
        }
        return false;
    }
    public boolean regionEnterEnabled() { return regionEnterEnabled; }
    public boolean regionLeaveEnabled() { return regionLeaveEnabled; }
    public boolean homeCancelOnMove() { return homeCancelOnMove; }
    public boolean homeCancelOnDamage() { return homeCancelOnDamage; }
    public boolean previewMessages() { return previewMessages; }

    public String regionEnterChannel() { return regionEnterChannel; }

    private static String channelValue(String raw) {
        String value = raw == null ? "CHAT" : raw.trim().toUpperCase(Locale.ROOT);
        return value.equals("ACTIONBAR") ? "CHAT" : value;
    }

    public String regionLeaveChannel() { return regionLeaveChannel; }
    public SoundSetting intruderAlert() { return intruderAlert; }
    public SoundSetting inviteReceived() { return inviteReceived; }

    public ParticleSetting particles() { return particles; }


    public boolean isFlagLocked(RegionFlag flag) {
        return lockedFlags.contains(flag);
    }

}
