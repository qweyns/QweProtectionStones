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
        // запись всей базы — не в главном потоке и через очередь:
        // прямой вызов DAO обгонял отложенные удаления и воскресал снесённые приваты
        plugin.getSchedulers().runAsync(() -> {
            int count = plugin.getRegionStorage().saveSnapshot(plugin.getRegionManager().getAllRegions());
            plugin.getSchedulers().runNextTick(() -> sender.sendMessage(
                    plugin.getLanguageManager().getMessage("admin_saved", "%count%", String.valueOf(count))));
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
        boolean stonesOnly = source.equals("protectionstones") || source.equals("ps");
        boolean griefPrevention = source.equals("griefprevention") || source.equals("gp");
        if (!stonesOnly && !griefPrevention && !source.equals("worldguard") && !source.equals("wg")) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_import_usage"));
            return;
        }

        // границы миров читаем в потоке сервера, файлы парсим вне его
        java.util.Map<String, int[]> worldBounds = new java.util.HashMap<>();
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            worldBounds.put(world.getName(), new int[]{world.getMinHeight(), world.getMaxHeight() - 1});
        }

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_import_started", "%source%", source));

        plugin.getSchedulers().runAsync(() -> {
            org.qweyns.qweprotectstones.features.importer.RegionImporter importer =
                    new org.qweyns.qweprotectstones.features.importer.RegionImporter(plugin);
            org.qweyns.qweprotectstones.features.importer.RegionImporter.Pending parsed = griefPrevention
                    ? importer.parseGriefPrevention(worldBounds)
                    : importer.parseWorldGuard(stonesOnly, worldBounds);

            // регистрация и голограммы — только в потоке сервера
            plugin.getSchedulers().runNextTick(() -> {
                registerImported(sender, parsed.regions(), parsed.skipped(), parsed.errors());
                importer.refreshMaps();
            });
        });
    }

    private void registerImported(CommandSender sender, List<Region> parsed, int skipped, int errors) {
        int imported = 0;
        boolean holograms = plugin.getConfigManager().getConfig().getBoolean("import.create-holograms", false);
        for (Region region : parsed) {
            if (plugin.getRegionManager().importRegion(region)) {
                imported++;
                if (holograms) plugin.getHologramManager().createOrUpdateHologram(region);
            } else {
                skipped++;
            }
        }
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_import_done",
                "%imported%", String.valueOf(imported),
                "%skipped%", String.valueOf(skipped),
                "%errors%", String.valueOf(errors)));
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

        // файл парсим вне основного потока, регистрацию делаем в потоке сервера
        plugin.getSchedulers().runAsync(() -> {
            var restorer = new org.qweyns.qweprotectstones.features.importer.RegionRestorer(plugin);
            org.qweyns.qweprotectstones.features.importer.RegionRestorer.Parsed parsed;
            try {
                parsed = restorer.parse(file);
            } catch (java.io.IOException e) {
                plugin.getSchedulers().runNextTick(() -> sender.sendMessage(
                        plugin.getLanguageManager().getMessage("admin_restore_failed", "%error%", String.valueOf(e.getMessage()))));
                return;
            }

            plugin.getSchedulers().runNextTick(() -> {
                int restored = 0;
                int skipped = parsed.skipped();
                boolean holograms = plugin.getConfigManager().getConfig().getBoolean("import.create-holograms", false);
                for (Region region : parsed.regions()) {
                    if (plugin.getRegionManager().importRegion(region)) {
                        restored++;
                        if (holograms) plugin.getHologramManager().createOrUpdateHologram(region);
                    } else {
                        skipped++;
                    }
                }
                if (restored > 0) {
                    if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().redrawAll();
                    if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().updateAll();
                }
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_restore_done",
                        "%restored%", String.valueOf(restored),
                        "%skipped%", String.valueOf(skipped),
                        "%errors%", String.valueOf(parsed.errors())));
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
