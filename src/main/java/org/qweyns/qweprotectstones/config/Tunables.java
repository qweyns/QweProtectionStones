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

/**
 * Все числовые пороги, тайминги, звуки и правила доступа — в одном месте.
 *
 * <p>Раньше это были два десятка {@code private static final} в разных классах:
 * поменять кулдаун клика или длительность эффекта можно было только пересборкой.
 * Теперь значения читаются из config.yml один раз при старте и при
 * {@code /qps reload}, а в горячем пути остаётся чтение поля — обращения к
 * {@code FileConfiguration} на каждое событие больше нет.</p>
 */
public final class Tunables {

    private final QweProtectStones plugin;

    // --- тайминги и лимиты ---
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

    // --- звуки ---
    private SoundSetting menuDenied;
    private SoundSetting menuSuccess;
    private SoundSetting raidAttack;
    private SoundSetting raidDestroyed;
    private SoundSetting raidNearby;
    private SoundSetting intruderAlert;
    private SoundSetting inviteReceived;

    // --- частицы ---
    private ParticleSetting particles;

    // --- доступ ---
    private Map<TrustAction, TrustLevel> trustRequirements;
    private Set<RegionFlag> lockedFlags;
    private TrustLevel flagEditLevel;
    private TrustLevel trustEditLevel;

    /** Действия, для которых требуемый уровень доверия задаётся в конфиге. */
    public enum TrustAction {
        /** Двери, кнопки, рычаги, кровати. */
        INTERACT("interact", TrustLevel.ACCESS),
        /** Сундуки, бочки, печи, воронки. */
        CONTAINER("container", TrustLevel.CONTAINER),
        /** Ставить и ломать блоки. */
        BUILD("build", TrustLevel.BUILD),
        /** Бить и использовать сущности: рамки, стойки, вагонетки. */
        ENTITY("entity", TrustLevel.BUILD),
        /** Флаги, приглашения, покупка улучшений. */
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

    /** Перечитывает значения. Вызывается при старте и из {@code /qps reload}. */
    public void reload() {
        FileConfiguration cfg = plugin.getConfig();

        menuClickCooldownMs = positive(cfg.getLong("timings.menu_click_cooldown_ms", 300L), 300L);
        deleteConfirmMs = positive(cfg.getLong("timings.delete_confirm_seconds", 30L), 30L) * 1000L;
        denyMessageCooldownMs = positive(cfg.getLong("timings.deny_message_cooldown_ms", 2000L), 2000L);
        moveThrottleMs = positive(cfg.getLong("timings.move_throttle_ms", 300L), 300L);
        effectRefreshTicks = (int) positive(cfg.getLong("timings.effect_refresh_ticks", 40L), 40L);
        effectDurationTicks = (int) positive(cfg.getLong("timings.effect_duration_ticks", 60L), 60L);
        dbFlushTicks = positive(cfg.getLong("timings.db_flush_ticks", 60L), 60L);
        animationPeriodTicks = positive(cfg.getLong("timings.animation_period_ticks", 10L), 10L);
        // Окно осады живёт в siege.yml (переехало из timings).
        siegeWindowMs = positive(cfg.getLong("siege.window_seconds", 300L), 300L) * 1000L;

        maxAutoAddFriends = (int) bounded(cfg.getLong("limits.autoadd_friends", 50L), 1, 500);
        dbBatchSize = (int) bounded(cfg.getLong("limits.db_batch_size", 500L), 25, 10_000);
        logPageSize = (int) bounded(cfg.getLong("limits.log_page_size", 15L), 1, 100);
        logMaxPageSize = (int) bounded(cfg.getLong("limits.log_max_page_size", 50L), logPageSize, 500);
        findSpotMaxRings = (int) bounded(cfg.getLong("limits.findspot_rings", 24L), 1, 200);

        // Эффект должен жить дольше периода обновления, иначе между тиками
        // задачи он успевает истечь и игрок видит мигание.
        if (effectDurationTicks <= effectRefreshTicks) {
            effectDurationTicks = effectRefreshTicks + 20;
        }

        loadSounds(cfg);
        loadParticles(cfg);
        loadTrust(cfg);
        validateEffectTargets(cfg);
    }

    /**
     * Опечатка вроде {@code SPEED: MEMBER} раньше просто выключала эффект:
     * значение не совпадало ни с MEMBERS, ни с ENEMIES, и никто не получал ничего.
     * Теперь это видно в логе при старте.
     */
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
            // null означает «строка есть, но звук не распознан» либо «ключа нет».
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

    // ------------------------------------------------------------------
    // Доступ
    // ------------------------------------------------------------------

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
    public SoundSetting intruderAlert() { return intruderAlert; }
    public SoundSetting inviteReceived() { return inviteReceived; }

    public ParticleSetting particles() { return particles; }

    /** Какой уровень доверия нужен для действия — с учётом настроек сервера. */
    public TrustLevel required(TrustAction action) {
        return trustRequirements.getOrDefault(action, action.fallback());
    }

    /** Может ли игрок вообще менять этот флаг, или он закрыт админом. */
    public boolean isFlagLocked(RegionFlag flag) {
        return lockedFlags.contains(flag);
    }

    public Set<RegionFlag> lockedFlags() {
        return lockedFlags;
    }

    public TrustLevel flagEditLevel() { return flagEditLevel; }

    public TrustLevel memberEditLevel() { return trustEditLevel; }
}
