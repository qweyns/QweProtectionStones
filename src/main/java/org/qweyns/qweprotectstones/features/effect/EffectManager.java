package org.qweyns.qweprotectstones.features.effect;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class EffectManager implements Listener {

    private static final String ALERTS = "ALERTS";
    private static final String EXP_BOOST = "EXP_BOOST";

    private final QweProtectStones plugin;
    // Folia: события из разных потоков, карты потокобезопасные
    private final Map<UUID, UUID> currentRegions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMoveCheck = new ConcurrentHashMap<>();

    private final Cache<String, Long> alertCooldowns = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    public EffectManager(QweProtectStones plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        long period = plugin.getTunables().effectRefreshTicks();
        plugin.getSchedulers().runTimer(this::refreshAll, period, period);
    }

    private void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getSchedulers().runAtEntity(player, () -> {
                if (player.isOnline()) checkPlayer(player, true);
            });
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;

        UUID uuid = event.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();
        if (now - lastMoveCheck.getOrDefault(uuid, 0L) < plugin.getTunables().moveThrottleMs()) return;
        lastMoveCheck.put(uuid, now);

        checkPlayer(event.getPlayer(), false);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        plugin.getSchedulers().runAtEntityLater(player, () -> {
            if (player.isOnline()) checkPlayer(player, false);
        }, 1L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        checkPlayer(event.getPlayer(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        currentRegions.remove(uuid);
        lastMoveCheck.remove(uuid);
    }

    public void checkPlayer(Player player, boolean forceUpdate) {
        Region region = plugin.getRegionManager().getRegionAt(player.getLocation());
        UUID uuid = player.getUniqueId();

        UUID newRegionId = region == null ? null : region.getId();
        boolean changed = !Objects.equals(currentRegions.get(uuid), newRegionId);

        if (!forceUpdate && !changed) return;
        if (changed) {
            if (newRegionId == null) currentRegions.remove(uuid);
            else currentRegions.put(uuid, newRegionId);
        }
        if (region == null) return;

        List<String> effects = region.getEffects();
        if (effects.isEmpty()) return;

        boolean trusted = plugin.getProtectionService().has(region, player, TrustLevel.ACCESS);
        if (changed && !trusted) notifyOwnerAboutIntruder(region, player);

        for (String raw : effects) {
            applyEffect(player, region, trusted, raw);
        }
    }

    private void notifyOwnerAboutIntruder(Region region, Player intruder) {
        if (!region.hasEffect(ALERTS)) return;

        String cooldownKey = region.getId() + ":" + intruder.getName();
        if (alertCooldowns.getIfPresent(cooldownKey) != null) return;
        alertCooldowns.put(cooldownKey, System.currentTimeMillis());

        plugin.getNotificationManager().sendIntruderAlert(region, intruder.getName());
    }

    private void applyEffect(Player player, Region region, boolean trusted, String raw) {
        String[] parts = raw.split(":");
        String name = parts[0].toUpperCase(Locale.ROOT);
        if (name.equals(ALERTS) || name.equals(EXP_BOOST)) return;

        PotionEffectType type = potionType(name);
        if (type == null) return;

        String target = plugin.getConfigManager().getEffectTarget(name);
        boolean applies = (target.equals("MEMBERS") && trusted) || (target.equals("ENEMIES") && !trusted);
        if (!applies) return;

        int amplifier = parseAmplifier(parts, region, raw);

        int duration = plugin.getTunables().effectDurationTicks();

        // чужой более сильный эффект не перебиваем
        PotionEffect active = player.getPotionEffect(type);
        if (active != null) {
            if (active.getAmplifier() > amplifier) return;
            if (active.getAmplifier() == amplifier && active.getDuration() > duration + 40) return;
        }

        player.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false, true));
    }

    private int parseAmplifier(String[] parts, Region region, String raw) {
        if (parts.length < 2) return 0;
        try {
            return Math.max(0, Math.min(255, Integer.parseInt(parts[1].trim())));
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Некорректный уровень эффекта '" + raw + "' у привата "
                    + region.getShortId() + " — использую 0.");
            return 0;
        }
    }

    public static PotionEffectType potionType(String name) {
        if (name == null || name.isBlank()) return null;
        NamespacedKey key = NamespacedKey.fromString(name.trim().toLowerCase(Locale.ROOT));
        return key != null ? Registry.POTION_EFFECT_TYPE.get(key) : null;
    }

    public void addCustomEffect(Region region, String effectName, int amplifier) {
        if (region == null) return;

        String normalized = effectName.toUpperCase(Locale.ROOT);
        int level = Math.max(0, Math.min(255, amplifier));

        region.getEffects().removeIf(effect -> effect.toUpperCase(Locale.ROOT).startsWith(normalized + ":"));
        region.getEffects().add(normalized + ":" + level);
        // без этого версия не меняется и открытые меню не узнают о покупке
        region.touch();
        plugin.getRegionStorage().saveNow(region);

        refreshAll();
    }

    public void clear() {
        currentRegions.clear();
        lastMoveCheck.clear();
    }
}
