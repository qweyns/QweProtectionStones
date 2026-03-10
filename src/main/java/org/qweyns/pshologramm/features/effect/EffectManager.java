package org.qweyns.pshologramm.features.effect;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.espi.protectionstones.PSRegion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.models.RegionData;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class EffectManager implements Listener {
    private final PSHologramm plugin;
    private final Map<UUID, String> currentRegions = new HashMap<>();
    private final Map<UUID, Long> lastMoveCheck = new HashMap<>();

    private final Cache<String, Long> alertCooldowns = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    public EffectManager(PSHologramm plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) checkPlayerRegion(p, true);
        }, 40L, 40L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        long now = System.currentTimeMillis();
        if (now - lastMoveCheck.getOrDefault(event.getPlayer().getUniqueId(), 0L) < 300L) return;
        lastMoveCheck.put(event.getPlayer().getUniqueId(), now);
        checkPlayerRegion(event.getPlayer(), false);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) { Bukkit.getScheduler().runTaskLater(plugin, () -> checkPlayerRegion(event.getPlayer(), false), 1L); }
    @EventHandler
    public void onJoin(PlayerJoinEvent event) { checkPlayerRegion(event.getPlayer(), false); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        currentRegions.remove(uuid);
        lastMoveCheck.remove(uuid);
    }

    public void checkPlayerRegion(Player player, boolean forceUpdate) {
        PSRegion region = PSRegion.fromLocation(player.getLocation());
        UUID uuid = player.getUniqueId();
        String oldRegion = currentRegions.get(uuid);
        String newRegion = region != null ? region.getId() : null;

        boolean changed = !Objects.equals(oldRegion, newRegion);
        if (!forceUpdate && !changed) return;

        if (changed) {
            currentRegions.put(uuid, newRegion);
        }

        if (region != null) {
            boolean isMember = region.isOwner(uuid) || region.isMember(uuid);
            RegionData rd = plugin.getStorageManager().getRegion(region.getId());
            List<String> effectsStr = rd != null ? rd.getEffects() : null;

            if (effectsStr != null && !effectsStr.isEmpty()) {
                if (changed && !isMember && effectsStr.stream().anyMatch(e -> e.startsWith("ALERTS"))) {
                    String cdKey = region.getId() + ":" + player.getName();
                    if (alertCooldowns.getIfPresent(cdKey) == null) {
                        alertCooldowns.put(cdKey, System.currentTimeMillis());
                        String owner = rd.getOwner() != null ? rd.getOwner() : "";
                        plugin.getNotificationManager().sendIntruderAlert(region.getId(), owner, region.getType(), player.getName());
                    }
                }

                for (String effStr : effectsStr) {
                    String[] split = effStr.split(":");
                    String effName = split[0].toUpperCase();

                    if (effName.equals("ALERTS") || effName.equals("EXP_BOOST")) continue;

                    PotionEffectType type = PotionEffectType.getByName(effName);
                    if (type != null) {
                        String target = plugin.getConfigManager().getEffectTarget(effName);

                        if ((target.equals("MEMBERS") && isMember) || (target.equals("ENEMIES") && !isMember)) {
                            int amplifier = split.length > 1 ? Integer.parseInt(split[1]) : 0;

                            PotionEffect active = player.getPotionEffect(type);
                            if (active != null) {
                                if (active.getAmplifier() > amplifier) continue;
                                if (active.getAmplifier() == amplifier && active.getDuration() > 100) continue;
                            }

                            player.addPotionEffect(new PotionEffect(type, 60, amplifier, true, false, true));
                        }
                    }
                }
            }
        }
    }

    public void addCustomEffect(String regionId, String effectName, int amplifier) {
        RegionData rd = plugin.getStorageManager().getRegion(regionId);
        if (rd != null) {
            rd.getEffects().removeIf(e -> e.startsWith(effectName + ":"));
            rd.getEffects().add(effectName + ":" + amplifier);
            plugin.getStorageManager().forceSave(rd);
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) checkPlayerRegion(p, true);
            });
        }
    }
}
