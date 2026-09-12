package org.qweyns.qweprotectstones.commands.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;

import java.io.File;
import java.util.List;
import java.util.Locale;

/** Системные админ-команды: перезагрузка, обход, статистика, экспорт, импорт, отладка. */
public class SystemAdminCommands {

    private final QweProtectStones plugin;
    private final long enabledAt = System.currentTimeMillis();

    public SystemAdminCommands(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public void reload(CommandSender sender) {
        plugin.reloadEverything();
        sender.sendMessage(plugin.getLanguageManager().getMessage("reload_success"));
    }

    public void bypass(CommandSender sender, Player player) {
        if (player == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("players_only"));
            return;
        }

        boolean enabled = plugin.getBypassManager().toggle(player);
        plugin.getCriticalFileLogger().log("BYPASS",
                "player=" + player.getName() + " enabled=" + enabled + " by=" + sender.getName());
        player.sendMessage(plugin.getLanguageManager().getMessage(enabled ? "bypass_enabled" : "bypass_disabled"));
    }

    public void save(CommandSender sender) {
        // запись всей базы — не в главном потоке
        List<Region> regions = List.copyOf(plugin.getRegionManager().getAllRegions());
        plugin.getSchedulers().runAsync(() -> {
            plugin.getRegionStorage().saveAll(regions);
            plugin.getSchedulers().runNextTick(() -> sender.sendMessage(
                    plugin.getLanguageManager().getMessage("admin_saved", "%count%", String.valueOf(regions.size()))));
        });
    }

    public void stats(CommandSender sender) {
        var lm = plugin.getLanguageManager();
        sender.sendMessage(lm.getMessage("admin_stats",
                "%regions%", String.valueOf(plugin.getRegionManager().size()),
                "%types%", String.valueOf(plugin.getRegionTypes().all().size()),
                "%owners%", String.valueOf(plugin.getRegionManager().ownersCount())));

        var market = plugin.getMarketManager();
        sender.sendMessage(lm.getMessage("admin_stats_market",
                "%sales%", String.valueOf(market.salesCount()),
                "%rented%", String.valueOf(market.rentedCount()),
                "%listings%", String.valueOf(market.rentalListingsCount()),
                "%penalties%", String.valueOf(plugin.getPenaltyManager().activeCount())));

        long last = plugin.getRegionStorage().lastFlushMillis();
        String flushAgo = last == 0 ? "-" : String.valueOf((System.currentTimeMillis() - last) / 1000);
        sender.sendMessage(lm.getMessage("admin_stats_cache",
                "%pending%", String.valueOf(plugin.getRegionStorage().pendingCount()),
                "%flush%", flushAgo));

        java.util.Map<String, Long> byType = new java.util.TreeMap<>();
        for (Region region : plugin.getRegionManager().getAllRegions()) {
            byType.merge(region.getTypeId(), 1L, Long::sum);
        }
        StringBuilder typesLine = new StringBuilder();
        for (java.util.Map.Entry<String, Long> entry : byType.entrySet()) {
            if (!typesLine.isEmpty()) typesLine.append(", ");
            typesLine.append(entry.getKey()).append(" — ").append(entry.getValue());
        }
        sender.sendMessage(lm.getMessage("admin_stats_types",
                "%types%", typesLine.isEmpty() ? "-" : typesLine.toString()));
    }

    public void export(CommandSender sender) {
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_export_started"));

        plugin.getSchedulers().runAsync(() -> {
            try {
                File file = plugin.getRegionExporter().export();
                plugin.getSchedulers().runNextTick(() -> sender.sendMessage(
                        plugin.getLanguageManager().getMessage("admin_export_done", "%file%", file.getName())));
            } catch (java.io.IOException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Не удалось выгрузить приваты", e);
                plugin.getSchedulers().runNextTick(() -> sender.sendMessage(
                        plugin.getLanguageManager().getMessage("admin_export_failed", "%error%", String.valueOf(e.getMessage()))));
            }
        });
    }

    public void cleanup(CommandSender sender) {
        plugin.getCriticalFileLogger().log("ADMIN_CLEANUP", "by=" + sender.getName());
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_cleanup_started"));
        plugin.getAbandonedRegionTask().sweep();
    }

    public void importRegions(CommandSender sender, String[] args) {
        String source = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        org.qweyns.qweprotectstones.features.importer.RegionImporter importer =
                new org.qweyns.qweprotectstones.features.importer.RegionImporter(plugin);

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_import_started", "%source%", source));
        org.qweyns.qweprotectstones.features.importer.RegionImporter.Result result;
        switch (source) {
            case "worldguard", "wg" -> result = importer.importFromWorldGuard(false);
            case "protectionstones", "ps" -> result = importer.importFromWorldGuard(true);
            case "griefprevention", "gp" -> result = importer.importFromGriefPrevention();
            default -> {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_import_usage"));
                return;
            }
        }

        importer.refreshMaps();
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_import_done",
                "%imported%", String.valueOf(result.imported()),
                "%skipped%", String.valueOf(result.skipped()),
                "%errors%", String.valueOf(result.errors())));
    }

    public void restore(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_restore_usage"));
            File folder = new File(plugin.getDataFolder(), "exports");
            File[] files = folder.listFiles((dir, name) -> name.startsWith("regions_") && name.endsWith(".json"));
            if (files != null && files.length > 0) {
                StringBuilder list = new StringBuilder();
                for (int i = 0; i < files.length && i < 10; i++) {
                    if (i > 0) list.append(", ");
                    list.append(files[i].getName());
                }
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_restore_files", "%files%", list.toString()));
            }
            return;
        }

        // только имя файла, чтобы ../ не вышел за пределы exports
        String fileName = args[1].replace("..", "").replace('/', '_').replace('\\', '_');
        File file = new File(new File(plugin.getDataFolder(), "exports"), fileName);
        if (!file.isFile()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_restore_not_found", "%file%", fileName));
            return;
        }

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_restore_started", "%file%", fileName));

        // парсим вне основного потока
        plugin.getSchedulers().runAsync(() -> {
            var restorer = new org.qweyns.qweprotectstones.features.importer.RegionRestorer(plugin);
            org.qweyns.qweprotectstones.features.importer.RegionRestorer.Result result;
            try {
                result = restorer.restore(file);
            } catch (java.io.IOException e) {
                plugin.getSchedulers().runNextTick(() -> sender.sendMessage(
                        plugin.getLanguageManager().getMessage("admin_restore_failed", "%error%", String.valueOf(e.getMessage()))));
                return;
            }

            plugin.getSchedulers().runNextTick(() -> {
                if (result.restored() > 0) {
                    if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().redrawAll();
                    if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().updateAll();
                }
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_restore_done",
                        "%restored%", String.valueOf(result.restored()),
                        "%skipped%", String.valueOf(result.skipped()),
                        "%errors%", String.valueOf(result.errors())));
            });
        });
    }

    public void backup(CommandSender sender) {
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_backup_started"));
        plugin.getBackupTask().run();
    }

    public void debug(CommandSender sender) {
        long uptimeMinutes = (System.currentTimeMillis() - enabledAt) / 60_000L;
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_debug_header",
                "%version%", plugin.getPluginMeta().getVersion(),
                "%uptime%", String.valueOf(uptimeMinutes)));

        String dbType = plugin.getConfigManager().getConfig().getString("database.type", "SQLITE");
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_debug_storage",
                "%db%", dbType.toUpperCase(Locale.ROOT),
                "%pending%", String.valueOf(plugin.getRegionStorage().pendingCount()),
                "%regions%", String.valueOf(plugin.getRegionManager().size()),
                "%types%", String.valueOf(plugin.getRegionTypes().all().size())));

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_debug_hooks",
                "%vault%", mark(plugin.getVaultHook().isEnabled()),
                "%points%", mark(plugin.getPlayerPointsHook().isEnabled()),
                "%papi%", mark(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null),
                "%dynmap%", mark(plugin.getDynmapIntegration() != null && plugin.getDynmapIntegration().isActive()),
                "%bluemap%", mark(plugin.getBlueMapIntegration() != null && plugin.getBlueMapIntegration().isActive()),
                "%discordsrv%", mark(plugin.getDiscordSrvHook() != null && plugin.getDiscordSrvHook().isActive())));

        Runtime rt = Runtime.getRuntime();
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_debug_runtime",
                "%folia%", plugin.getSchedulers().isFolia() ? "Folia" : "Paper/Spigot",
                "%api%", org.qweyns.qweprotectstones.api.QpsApi.isAvailable() ? "OK" : "OFF",
                "%memory%", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024 + "/" + rt.maxMemory() / 1024 / 1024 + " MB",
                "%threads%", String.valueOf(Thread.activeCount())));

        if (plugin.getConfigManager().getConfig().getBoolean("debug.verbose", false)) {
            for (RegionType type : plugin.getRegionTypes().all()) {
                int count = 0;
                for (Region region : plugin.getRegionManager().getAllRegions()) {
                    if (region.getTypeId().equals(type.id())) count++;
                }
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_debug_type",
                        "%type%", type.id(), "%count%", String.valueOf(count)));
            }
        }
    }

    private String mark(boolean value) {
        return value ? "+" : "-";
    }
}
