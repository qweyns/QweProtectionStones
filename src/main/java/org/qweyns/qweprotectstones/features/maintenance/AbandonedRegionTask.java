package org.qweyns.qweprotectstones.features.maintenance;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.event.RegionDeleteEvent;
import org.qweyns.qweprotectstones.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AbandonedRegionTask {

    private final QweProtectStones plugin;

    public AbandonedRegionTask(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    private Schedulers.Task task;

    public void start() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!isEnabled()) return;

        long intervalTicks = TimeUnit.MINUTES.toSeconds(checkIntervalMinutes()) * 20L;

        task = plugin.getSchedulers().runTimer(this::sweep, 20L * 60, intervalTicks);

        plugin.getLogger().info("Автоочистка заброшенных приватов включена: срок "
                + inactiveDays() + " дн., проверка каждые " + checkIntervalMinutes() + " мин.");
    }

    private boolean isEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("abandoned.enable", false);
    }

    private int inactiveDays() {
        return Math.max(1, plugin.getConfigManager().getConfig().getInt("abandoned.inactive_days", 60));
    }

    private int checkIntervalMinutes() {
        return Math.max(5, plugin.getConfigManager().getConfig().getInt("abandoned.check_interval_minutes", 60));
    }

    private boolean keepUpgraded() {
        return plugin.getConfigManager().getConfig().getBoolean("abandoned.keep_upgraded", true);
    }

    public void sweep() {
        plugin.getSchedulers().runAsync(() -> {
            Map<UUID, Long> lastSeen = new java.util.HashMap<>(plugin.getRegionStorage().loadLastSeen());
            // getLastPlayed читает файл игрока с диска — делаем это здесь, вне основного потока,
            // иначе на тысячах приватов проход подвешивал сервер.
            java.util.Set<UUID> players = new java.util.HashSet<>();
            for (Region region : plugin.getRegionManager().getAllRegions()) {
                if (region.getOwnerId() != null) players.add(region.getOwnerId());
                for (var member : region.getMembers()) players.add(member.uuid());
            }
            for (UUID player : players) {
                lastSeen.merge(player, Bukkit.getOfflinePlayer(player).getLastPlayed(), Math::max);
            }
            plugin.getSchedulers().runNextTick(() -> removeAbandoned(lastSeen));
        });
    }

    private void removeAbandoned(Map<UUID, Long> lastSeen) {
        long now = System.currentTimeMillis();
        long threshold = now - TimeUnit.DAYS.toMillis(inactiveDays());
        // Отсчёт не раньше момента, когда плагин начал следить за входами: после восстановления
        // бэкапа на чистую базу иначе все приваты выглядели бы заброшенными сразу.
        long trackingSince = trackingSince(now);
        java.util.Set<String> activeRoles = activeMemberRoles();
        List<Region> doomed = new ArrayList<>();

        for (Region region : plugin.getRegionManager().getAllRegions()) {
            UUID owner = region.getOwnerId();
            if (owner == null) continue;
            if (lastActivity(owner, lastSeen, region, trackingSince) > threshold) continue;

            // активные участники с выбранными ролями продлевают жизнь привата
            if (hasActiveMember(region, activeRoles, lastSeen, trackingSince, threshold)) continue;

            if (keepUpgraded() && region.getDurability() > startDurabilityOf(region)) continue;

            // выставленные на продажу и в аренду не трогаем — вместе с приватом сгорали бы сделки
            if (skipListed() && isListed(region)) continue;

            doomed.add(region);
        }

        if (doomed.isEmpty()) return;

        int limit = maxPerSweep();
        if (limit > 0 && doomed.size() > limit) {
            plugin.getLogger().warning("Автоочистка: кандидатов " + doomed.size() + ", за проход удаляю не больше "
                    + limit + " (abandoned.max_per_sweep).");
            doomed = doomed.subList(0, limit);
        }

        if (dryRun()) {
            plugin.getLogger().info("Автоочистка (dry_run): удалила бы " + doomed.size() + " приват(ов):");
            for (Region region : doomed) {
                plugin.getLogger().info("  " + region.getShortId() + " владелец " + region.getOwnerName()
                        + " (" + region.getWorldName() + " " + region.getCoreX() + " " + region.getCoreY() + " " + region.getCoreZ() + ")");
            }
            return;
        }

        int removed = 0;
        for (Region region : doomed) {
            if (!plugin.getRegionManager().deleteRegion(region, RegionDeleteEvent.Reason.EXPIRED, null)) continue;

            plugin.getRegionLifecycleListener().cleanupVisuals(region);
            clearCoreBlock(region);
            removed++;
        }
        plugin.getLogger().info("Автоочистка: удалено заброшенных приватов — " + removed);
    }

    /** Последняя активность игрока: онлайн, запись о входе, данные сервера, иначе — не раньше начала слежения. */
    private long lastActivity(UUID player, Map<UUID, Long> lastSeen, Region region, long trackingSince) {
        if (Bukkit.getPlayer(player) != null) return Long.MAX_VALUE;
        long best = lastSeen.getOrDefault(player, 0L);
        if (best > 0) return best;
        return Math.max(region.getCreatedAt(), trackingSince);
    }

    private boolean hasActiveMember(Region region, java.util.Set<String> activeRoles, Map<UUID, Long> lastSeen,
                                    long trackingSince, long threshold) {
        if (activeRoles.isEmpty()) return false;
        boolean any = activeRoles.contains("*");
        for (var member : region.getMembers()) {
            if (!any && !activeRoles.contains(member.role())) continue;
            if (lastActivity(member.uuid(), lastSeen, region, trackingSince) > threshold) return true;
        }
        return false;
    }

    private java.util.Set<String> activeMemberRoles() {
        java.util.Set<String> result = new java.util.HashSet<>();
        for (String raw : plugin.getConfigManager().getConfig().getStringList("abandoned.active_member_roles")) {
            String id = raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (id.equals("*")) { result.add(id); continue; }
            // псевдонимы из roles.yml приводим к настоящему id
            result.add(org.qweyns.qweprotectstones.regions.TrustLevel.parse(id)
                    .map(org.qweyns.qweprotectstones.regions.TrustLevel::id).orElse(id));
        }
        return result;
    }

    /** Когда автоочистка впервые увидела этот сервер; хранится в data/abandoned-since.txt. */
    private long trackingSince(long now) {
        java.nio.file.Path file = plugin.getDataFolder().toPath().resolve("data").resolve("abandoned-since.txt");
        try {
            if (java.nio.file.Files.isRegularFile(file)) {
                return Long.parseLong(java.nio.file.Files.readString(file).trim());
            }
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, Long.toString(now));
        } catch (java.io.IOException | NumberFormatException e) {
            plugin.getLogger().warning("Автоочистка: не удалось прочитать/записать " + file + ": " + e.getMessage());
        }
        return now;
    }

    private boolean dryRun() {
        return plugin.getConfigManager().getConfig().getBoolean("abandoned.dry_run", false);
    }

    private int maxPerSweep() {
        return Math.max(0, plugin.getConfigManager().getConfig().getInt("abandoned.max_per_sweep", 50));
    }

    private boolean skipListed() {
        return plugin.getConfigManager().getConfig().getBoolean("abandoned.skip_listed", true);
    }

    private boolean isListed(Region region) {
        return plugin.getMarketManager().getSale(region) != null
                || plugin.getMarketManager().getRental(region) != null;
    }

    private int startDurabilityOf(Region region) {
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        return type != null ? type.startDurability() : 1;
    }

    private void clearCoreBlock(Region region) {
        Location core = region.getCoreLocation();
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        if (core == null || type == null) return;

        org.qweyns.qweprotectstones.utils.CoreBlocks.clearLater(plugin, region, core);
    }
}
