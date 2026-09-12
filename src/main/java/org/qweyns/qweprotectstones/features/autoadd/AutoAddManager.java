package org.qweyns.qweprotectstones.features.autoadd;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.event.RegionEvents;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AutoAddManager implements Listener {

    private final QweProtectStones plugin;

    private final Map<UUID, Set<String>> autoAddLists = new ConcurrentHashMap<>();
    private final Set<UUID> toggledOff = ConcurrentHashMap.newKeySet();

    public AutoAddManager(QweProtectStones plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        Bukkit.getOnlinePlayers().forEach(player -> loadPlayer(player.getUniqueId()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        saveAsync(uuid);
        autoAddLists.remove(uuid);
        toggledOff.remove(uuid);
    }

    private void loadPlayer(UUID uuid) {
        plugin.getRegionStorage().loadAutoAddAsync(uuid, (friends, isToggledOff) -> {
            // колбэк приходит на главном потоке, не перетираем, а дополняем

            if (Bukkit.getPlayer(uuid) == null) return;

            if (!friends.isEmpty()) {
                autoAddLists.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).addAll(friends);
            }
            if (isToggledOff) toggledOff.add(uuid);
        });
    }

    private void saveAsync(UUID uuid) {
        plugin.getRegionStorage().saveAutoAddAsync(uuid, autoAddLists.getOrDefault(uuid, Set.of()), toggledOff.contains(uuid));
    }

    public void toggle(Player player) {
        UUID uuid = player.getUniqueId();

        if (toggledOff.remove(uuid)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("autoadd_enabled"));
        } else {
            toggledOff.add(uuid);
            player.sendMessage(plugin.getLanguageManager().getMessage("autoadd_disabled"));
        }
        saveAsync(uuid);
    }

    public void addPlayer(Player owner, String target) {
        if (target == null || target.isBlank()) return;

        UUID uuid = owner.getUniqueId();
        Set<String> list = autoAddLists.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        String normalized = target.toLowerCase(Locale.ROOT);

        int maxFriends = plugin.getTunables().maxAutoAddFriends();
        if (list.size() >= maxFriends && !list.contains(normalized)) {
            owner.sendMessage(plugin.getLanguageManager().getMessage("autoadd_list_full",
                    "%limit%", String.valueOf(maxFriends)));
            return;
        }

        list.add(normalized);
        owner.sendMessage(plugin.getLanguageManager().getMessage("autoadd_player_added", "%player%", target));
        saveAsync(uuid);
    }

    public void removePlayer(Player owner, String target) {
        UUID uuid = owner.getUniqueId();
        Set<String> list = autoAddLists.get(uuid);

        if (list != null && target != null && list.remove(target.toLowerCase(Locale.ROOT))) {
            owner.sendMessage(plugin.getLanguageManager().getMessage("autoadd_player_removed", "%player%", target));
            saveAsync(uuid);
        } else {
            owner.sendMessage(plugin.getLanguageManager().getMessage("autoadd_player_not_found"));
        }
    }

    public void showList(Player owner) {
        Set<String> list = autoAddLists.getOrDefault(owner.getUniqueId(), Set.of());
        owner.sendMessage(plugin.getLanguageManager().getMessage("autoadd_list_header"));

        if (list.isEmpty()) {
            owner.sendMessage(plugin.getLanguageManager().getMessage("autoadd_list_empty"));
        } else {
            owner.sendMessage(plugin.getLanguageManager().getMessage("autoadd_list_players",
                    "%players%", String.join(", ", list)));
        }
    }

    public List<String> getList(Player owner) {
        return new ArrayList<>(autoAddLists.getOrDefault(owner.getUniqueId(), Set.of()));
    }

    public void applyToRegion(Player owner, Region region) {
        if (toggledOff.contains(owner.getUniqueId())) return;

        Set<String> list = autoAddLists.get(owner.getUniqueId());
        if (list == null || list.isEmpty()) return;

        int added = 0;
        for (String name : list) {
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
            if (target == null || target.getUniqueId() == null) continue;
            if (region.isOwner(target.getUniqueId())) continue;

            if (RegionEvents.fireMemberChange(region, null, target.getUniqueId(),
                    target.getName() != null ? target.getName() : name,
                    org.qweyns.qweprotectstones.regions.event.RegionMemberChangeEvent.Action.TRUST, TrustLevel.BUILD)) {
                continue;
            }
            region.setMember(target.getUniqueId(), target.getName() != null ? target.getName() : name, TrustLevel.BUILD);
            added++;
        }

        if (added > 0) {
            plugin.getRegionStorage().save(region);
            owner.sendMessage(plugin.getLanguageManager().getMessage("autoadd_applied", "%count%", String.valueOf(added)));
        }
    }

    public void saveAllOnline() {
        Set<UUID> saved = ConcurrentHashMap.newKeySet();

        autoAddLists.forEach((uuid, friends) -> {
            plugin.getRegionStorage().saveAutoAddSync(uuid, friends, toggledOff.contains(uuid));
            saved.add(uuid);
        });
        toggledOff.stream()
                .filter(uuid -> !saved.contains(uuid))
                .forEach(uuid -> plugin.getRegionStorage().saveAutoAddSync(uuid, Set.of(), true));
    }
}
