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
    private boolean regionEnterEnabled;
    private boolean regionLeaveEnabled;
    private String regionEnterChannel;
    private String regionLeaveChannel;

    private Map<TrustAction, TrustLevel> trustRequirements;
    private Set<RegionFlag> lockedFlags;
    private TrustLevel flagEditLevel;
    private TrustLevel trustEditLevel;

    public enum TrustAction {

        INTERACT("interact", TrustLevel.ACCESS),

        CONTAINER("container", TrustLevel.CONTAINER),

        BUILD("build", TrustLevel.BUILD),

        ENTITY("entity", TrustLevel.BUILD),

        MANAGE("manage", TrustLevel.MANAGER);

        private final String key;
        private final TrustLevel fallback;

        TrustAction(String key, TrustLevel fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        public String key() { return key; }

        public TrustLevel fallback() { return fallback; }
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
        menuDenied = sound(cfg, "menu_denied", "ENTITY_VILLAGER_NO:1.0:1.0");
        menuSuccess = sound(cfg, "menu_success", "BLOCK_ANVIL_USE:1.0:1.0");
        raidAttack = sound(cfg, "raid_attack", "ENTITY_ENDER_DRAGON_GROWL:1.0:1.0");
        raidDestroyed = sound(cfg, "raid_destroyed", "ENTITY_WITHER_DEATH:1.0:1.0");
        raidNearby = sound(cfg, "raid_nearby", "ENTITY_WITHER_SHOOT:0.4:0.7");
        intruderAlert = sound(cfg, "intruder_alert", "BLOCK_NOTE_BLOCK_BELL:1.0:1.0");
        inviteReceived = sound(cfg, "invite_received", "ENTITY_EXPERIENCE_ORB_PICKUP:1.0:1.2");
    }

    private SoundSetting sound(FileConfiguration cfg, String key, String defaultValue) {
        SoundSetting fallback = SoundSetting.parse(defaultValue, SoundSetting.NONE);
        String raw = cfg.getString("sounds." + key);

        SoundSetting parsed = SoundSetting.parse(raw, null);
        if (parsed == null) {

            if (raw != null && !raw.isBlank()) {
                plugin.getLogger().warning("sounds." + key + ": неизвестный звук '" + raw
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
        Map<TrustAction, TrustLevel> requirements = new EnumMap<>(TrustAction.class);
        for (TrustAction action : TrustAction.values()) {
            String raw = cfg.getString("trust.required." + action.key());
            TrustLevel level = TrustLevel.parse(raw).orElse(null);
            if (level == null && raw != null && !raw.isBlank()) {
                plugin.getLogger().warning("trust.required." + action.key() + ": неизвестный уровень '"
                        + raw + "', использую " + action.fallback().key());
            }
            requirements.put(action, level != null ? level : action.fallback());
        }
        trustRequirements = Map.copyOf(requirements);

        flagEditLevel = TrustLevel.parse(cfg.getString("trust.flag_edit_level")).orElse(TrustLevel.MANAGER);
        trustEditLevel = TrustLevel.parse(cfg.getString("trust.member_edit_level")).orElse(TrustLevel.MANAGER);

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
        regionEnterEnabled = cfg.getBoolean("region-messages.enter.enabled", true);
        regionLeaveEnabled = cfg.getBoolean("region-messages.leave.enabled", true);
        // канал ACTIONBAR выпилен: старые конфиги читаем как CHAT
        regionEnterChannel = channelValue(cfg.getString("region-messages.enter.channel", "CHAT"));
        regionLeaveChannel = channelValue(cfg.getString("region-messages.leave.channel", "CHAT"));

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
    public boolean regionEnterEnabled() { return regionEnterEnabled; }
    public boolean regionLeaveEnabled() { return regionLeaveEnabled; }

    public String regionEnterChannel() { return regionEnterChannel; }

    private static String channelValue(String raw) {
        String value = raw == null ? "CHAT" : raw.trim().toUpperCase(Locale.ROOT);
        return value.equals("ACTIONBAR") ? "CHAT" : value;
    }

    public String regionLeaveChannel() { return regionLeaveChannel; }
    public SoundSetting intruderAlert() { return intruderAlert; }
    public SoundSetting inviteReceived() { return inviteReceived; }

    public ParticleSetting particles() { return particles; }

    public TrustLevel required(TrustAction action) {
        return trustRequirements.getOrDefault(action, action.fallback());
    }

    public boolean isFlagLocked(RegionFlag flag) {
        return lockedFlags.contains(flag);
    }

    public TrustLevel flagEditLevel() { return flagEditLevel; }

    public TrustLevel memberEditLevel() { return trustEditLevel; }
}
