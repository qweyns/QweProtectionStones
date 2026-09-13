package org.qweyns.qweprotectstones;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionManager;
import org.qweyns.qweprotectstones.regions.RegionRateLimiter;
import org.qweyns.qweprotectstones.regions.RegionTypeRegistry;
import org.qweyns.qweprotectstones.regions.protection.BlockProtectionListener;
import org.qweyns.qweprotectstones.regions.protection.BypassManager;
import org.qweyns.qweprotectstones.regions.protection.RegionMovementListener;
import org.qweyns.qweprotectstones.regions.protection.EntityProtectionListener;
import org.qweyns.qweprotectstones.regions.protection.InteractProtectionListener;
import org.qweyns.qweprotectstones.regions.protection.ProtectionService;
import org.qweyns.qweprotectstones.commands.AdminCommand;
import org.qweyns.qweprotectstones.commands.RegionCommand;
import org.qweyns.qweprotectstones.commands.CommandRegistrar;
import org.qweyns.qweprotectstones.config.RegionConfig;
import org.qweyns.qweprotectstones.config.ConfigManager;
import org.qweyns.qweprotectstones.config.Tunables;
import org.qweyns.qweprotectstones.config.LanguageManager;
import org.qweyns.qweprotectstones.features.autoadd.AutoAddManager;
import org.qweyns.qweprotectstones.features.export.RegionExporter;
import org.qweyns.qweprotectstones.features.invite.InviteManager;
import org.qweyns.qweprotectstones.features.log.RegionActionLogger;
import org.qweyns.qweprotectstones.features.maintenance.AbandonedRegionTask;
import org.qweyns.qweprotectstones.features.maintenance.PlayerActivityListener;
import org.qweyns.qweprotectstones.features.map.DynmapIntegration;
import org.qweyns.qweprotectstones.features.map.BlueMapIntegration;
import org.qweyns.qweprotectstones.features.market.MarketManager;
import org.qweyns.qweprotectstones.features.effect.EffectManager;
import org.qweyns.qweprotectstones.features.effect.ExpBoostListener;
import org.qweyns.qweprotectstones.features.notification.NotificationManager;
import org.qweyns.qweprotectstones.features.penalty.PenaltyManager;
import org.qweyns.qweprotectstones.features.visual.RegionPreviewListener;
import org.qweyns.qweprotectstones.features.visual.VisualManager;
import org.qweyns.qweprotectstones.holograms.HologramManager;
import org.qweyns.qweprotectstones.hooks.PAPIExpansion;
import org.qweyns.qweprotectstones.hooks.PlayerPointsHook;
import org.qweyns.qweprotectstones.hooks.VaultHook;
import org.qweyns.qweprotectstones.listeners.RegionExplosionListener;
import org.qweyns.qweprotectstones.listeners.RegionInteractListener;
import org.qweyns.qweprotectstones.listeners.RegionLifecycleListener;
import org.qweyns.qweprotectstones.menus.MenuHolder;
import org.qweyns.qweprotectstones.menus.MenuManager;
import org.qweyns.qweprotectstones.scheduler.Schedulers;
import org.qweyns.qweprotectstones.api.QpsApi;
import org.qweyns.qweprotectstones.storage.RegionStorage;

import java.util.List;

public final class QweProtectStones extends JavaPlugin {

    public static final String PERMISSION_PREFIX = "qweprotectstones";

    private ConfigManager configManager;
    private RegionConfig regionConfig;
    private Tunables tunables;
    private LanguageManager languageManager;

    private Schedulers schedulers;
    private RegionTypeRegistry regionTypes;
    private RegionRateLimiter rateLimiter;
    private RegionStorage regionStorage;
    private RegionManager regionManager;
    private ProtectionService protectionService;
    private BypassManager bypassManager;

    private HologramManager hologramManager;
    private VisualManager visualManager;
    private AutoAddManager autoAddManager;
    private MenuManager menuManager;
    private EffectManager effectManager;
    private org.qweyns.qweprotectstones.features.effect.EffectPurchaseManager effectPurchaseManager;
    private org.qweyns.qweprotectstones.features.teleport.HomeWarmup homeWarmup;
    private NotificationManager notificationManager;
    private PenaltyManager penaltyManager;
    private InviteManager inviteManager;
    private RegionExporter regionExporter;
    private DynmapIntegration dynmapIntegration;
    private PAPIExpansion papiExpansion;
    private BlueMapIntegration blueMapIntegration;
    private org.qweyns.qweprotectstones.features.market.MarketManager marketManager;
    private org.qweyns.qweprotectstones.features.backup.BackupTask backupTask;
    private org.qweyns.qweprotectstones.features.update.UpdateChecker updateChecker;
    private org.qweyns.qweprotectstones.hooks.DiscordSrvHook discordSrvHook;
    private AbandonedRegionTask abandonedRegionTask;
    private RegionActionLogger actionLogger;
    private org.qweyns.qweprotectstones.features.log.CriticalFileLogger criticalFileLogger;
    private RegionPreviewListener previewListener;
    private org.qweyns.qweprotectstones.features.recipe.CoreRecipeManager coreRecipeManager;

    private RegionLifecycleListener regionLifecycleListener;
    private RegionMovementListener regionMovementListener;
    private RegionCommand regionCommand;

    private org.qweyns.qweprotectstones.api.SiegeService siegeService;
    private VaultHook vaultHook;
    private PlayerPointsHook playerPointsHook;
    private org.bukkit.event.Listener papiFallbackListener;

    @Override
    public void onEnable() {
        // планировщик первым, на нём вся асинхронность
        this.schedulers = Schedulers.create(this);
        this.configManager = new ConfigManager(this);
        this.regionConfig = new RegionConfig(this);
        this.tunables = new Tunables(this);

        this.languageManager = new LanguageManager(this);
        this.languageManager.init();

        this.regionTypes = new RegionTypeRegistry(this);
        this.regionTypes.load();
        this.coreRecipeManager = new org.qweyns.qweprotectstones.features.recipe.CoreRecipeManager(this);

        this.bypassManager = new BypassManager();
        this.rateLimiter = new RegionRateLimiter(this);
        this.protectionService = new ProtectionService(this);
        this.protectionService.reloadDenyCooldown();

        this.regionStorage = new RegionStorage(this);
        this.regionManager = new RegionManager(this);

        List<Region> loadedRegions;
        try {
            loadedRegions = regionStorage.init();
        } catch (RuntimeException e) {
            // без базы плагин бессмыслен и опасен: честно отключаемся, а не работаем вслепую
            getLogger().log(java.util.logging.Level.SEVERE, "База данных недоступна — плагин отключается", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.regionManager.loadAll(loadedRegions);
        this.regionManager.refreshTypeData();

        this.coreRecipeManager.reload();

        this.vaultHook = new VaultHook();
        this.vaultHook.setup();
        this.playerPointsHook = new PlayerPointsHook();
        this.playerPointsHook.setup(getLogger());
        this.discordSrvHook = new org.qweyns.qweprotectstones.hooks.DiscordSrvHook(this);
        this.discordSrvHook.setup();

        this.marketManager = new MarketManager(this);
        this.marketManager.load();

        PluginManager pm = getServer().getPluginManager();
        if (getConfigManager().getConfig().getBoolean("placeholders.enabled", true)) {
            if (pm.isPluginEnabled("PlaceholderAPI")) {
                papiExpansion = new PAPIExpansion(this);
                papiExpansion.register();
            } else {
                // PlaceholderAPI может подняться позже нас; после регистрации фолбэк снимаем
                papiFallbackListener = new org.bukkit.event.Listener() {
                    @org.bukkit.event.EventHandler
                    public void onPluginEnable(org.bukkit.event.server.PluginEnableEvent event) {
                        if (!event.getPlugin().getName().equals("PlaceholderAPI") || papiExpansion != null) return;
                        papiExpansion = new PAPIExpansion(QweProtectStones.this);
                        papiExpansion.register();
                        org.bukkit.event.HandlerList.unregisterAll(papiFallbackListener);
                        papiFallbackListener = null;
                    }
                };
                pm.registerEvents(papiFallbackListener, this);
            }
        }

        this.visualManager = new VisualManager(this);
        this.autoAddManager = new AutoAddManager(this);
        this.effectManager = new EffectManager(this);
        this.effectPurchaseManager = new org.qweyns.qweprotectstones.features.effect.EffectPurchaseManager(this);
        this.homeWarmup = new org.qweyns.qweprotectstones.features.teleport.HomeWarmup(this);
        this.notificationManager = new NotificationManager(this);
        this.penaltyManager = new PenaltyManager(this);
        this.menuManager = new MenuManager(this);
        this.inviteManager = new InviteManager(this);
        this.regionExporter = new RegionExporter(this);

        this.hologramManager = new HologramManager(this);
        this.hologramManager.init();

        // сервис осады раньше листенера взрывов: тот работает только через него
        this.siegeService = new org.qweyns.qweprotectstones.api.SiegeService(this);

        registerListeners(pm);
        registerCommands();

        this.abandonedRegionTask = new AbandonedRegionTask(this);
        this.abandonedRegionTask.start();
        this.marketManager.startExpiryTask();
        this.actionLogger.startPruning();

        this.criticalFileLogger = new org.qweyns.qweprotectstones.features.log.CriticalFileLogger(this);
        this.criticalFileLogger.start();
        this.backupTask = new org.qweyns.qweprotectstones.features.backup.BackupTask(this);
        this.backupTask.start();

        this.updateChecker = new org.qweyns.qweprotectstones.features.update.UpdateChecker(this);
        this.updateChecker.start();
        initMetrics();

        this.dynmapIntegration = new DynmapIntegration(this);
        this.dynmapIntegration.enable();

        this.blueMapIntegration = new BlueMapIntegration(this);
        this.blueMapIntegration.enable();

        getLogger().info("QweProtectStones v" + getPluginMeta().getVersion() + " запущен"
                + (schedulers.isFolia() ? " (режим Folia)" : "") + ": приватов — " + regionManager.size());

        QpsApi.init(this);
    }

    private void registerListeners(PluginManager pm) {
        this.regionLifecycleListener = new RegionLifecycleListener(this);
        this.regionMovementListener = new RegionMovementListener(this);

        pm.registerEvents(bypassManager, this);
        pm.registerEvents(regionLifecycleListener, this);
        pm.registerEvents(regionMovementListener, this);
        pm.registerEvents(new RegionExplosionListener(this, siegeService), this);
        pm.registerEvents(new RegionInteractListener(this), this);
        pm.registerEvents(new ExpBoostListener(this), this);

        pm.registerEvents(new BlockProtectionListener(this), this);
        pm.registerEvents(new InteractProtectionListener(this), this);
        pm.registerEvents(new EntityProtectionListener(this), this);
        pm.registerEvents(new org.qweyns.qweprotectstones.regions.protection.HopperProtectionListener(this), this);
        pm.registerEvents(new org.qweyns.qweprotectstones.regions.protection.BorderProtectionListener(this), this);

        this.actionLogger = new RegionActionLogger(this);
        this.previewListener = new RegionPreviewListener(this);

        pm.registerEvents(actionLogger, this);
        pm.registerEvents(previewListener, this);
        pm.registerEvents(new PlayerActivityListener(this), this);

        pm.registerEvents(homeWarmup, this);
    }

    private void registerCommands() {
        // имя команды из config.yml, поэтому регистрация через CommandMap

        String name = configManager.getCommandName();
        List<String> aliases = configManager.getCommandAliases();

        this.regionCommand = new RegionCommand(this, name, aliases);
        if (CommandRegistrar.register(this, regionCommand)) {
            getLogger().info("Команда игроков: /" + name
                    + (aliases.isEmpty() ? "" : " (алиасы: " + String.join(", ", aliases) + ")"));
        }

        PluginCommand adminCommand = getCommand("qweprotectstones");
        if (adminCommand != null) {
            AdminCommand executor = new AdminCommand(this);
            adminCommand.setExecutor(executor);
            adminCommand.setTabCompleter(executor);
        } else {
            getLogger().severe("Команда qweprotectstones не объявлена в plugin.yml — администрирование недоступно.");
        }
    }

    @Override
    public void onDisable() {
        // API закрываем первым, чтобы чужие плагины не ловили NPE
        QpsApi.shutdown();
        if (blueMapIntegration != null) blueMapIntegration.disable();
        if (dynmapIntegration != null) dynmapIntegration.disable();
        // иначе PlaceholderAPI держит мёртвый classloader после reload
        if (papiExpansion != null) papiExpansion.unregister();

        // меню первыми, close снимет задачи анимации
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory topInv = player.getOpenInventory().getTopInventory();
            if (topInv.getHolder() instanceof MenuHolder) player.closeInventory();
        }

        if (regionCommand != null) CommandRegistrar.unregister(this, regionCommand);
        if (notificationManager != null) notificationManager.close();
        if (updateChecker != null) updateChecker.close();
        if (visualManager != null) visualManager.shutdown();
        if (bypassManager != null) bypassManager.clear();
        if (rateLimiter != null) rateLimiter.clear();
        if (inviteManager != null) inviteManager.clear();
        if (previewListener != null) previewListener.clear();
        // буфер журнала критических операций сбрасываем на диск до выхода
        if (criticalFileLogger != null) criticalFileLogger.stop();
        if (schedulers != null) schedulers.cancelAll();
        if (autoAddManager != null) autoAddManager.saveAllOnline();
        if (hologramManager != null) hologramManager.deleteAll();

        if (regionStorage != null) {
            // очередь могла не успеть, сохраняем всё синхронно
            if (regionManager != null) regionStorage.saveAll(regionManager.getAllRegions());
            regionStorage.close();
        }
    }

    public void reloadEverything() {
        configManager.reload();
        regionConfig.reload();
        tunables.reload();
        languageManager.init();
        regionTypes.load();
        regionManager.refreshTypeData();
        coreRecipeManager.reload();
        menuManager.loadMenus();
        protectionService.reloadDenyCooldown();

        // таймеры перечитывают периоды из конфига
        regionStorage.restartFlushTask();
        effectManager.start();
        abandonedRegionTask.start();
        marketManager.startExpiryTask();
        backupTask.start();
        updateChecker.start();
        rateLimiter.reload();
        siegeService.reload();
        inviteManager.reload();
        criticalFileLogger.start();
        // перезапуск, а не только onEnable: enable/keep_days могли измениться в конфиге
        actionLogger.startPruning();

        hologramManager.restoreHolograms();
        if (dynmapIntegration != null) dynmapIntegration.redrawAll();

        if (blueMapIntegration != null) blueMapIntegration.enable();

        getLogger().info("Конфигурация перезагружена. Смена имени команды применится после перезапуска сервера.");
    }

    public Schedulers getSchedulers() { return schedulers; }

    public ConfigManager getConfigManager() { return configManager; }

    public RegionConfig getRegionConfig() { return regionConfig; }

    public Tunables getTunables() { return tunables; }
    public LanguageManager getLanguageManager() { return languageManager; }

    public RegionTypeRegistry getRegionTypes() { return regionTypes; }
    public RegionStorage getRegionStorage() { return regionStorage; }
    public RegionManager getRegionManager() { return regionManager; }
    public ProtectionService getProtectionService() { return protectionService; }

    public org.qweyns.qweprotectstones.api.SiegeService getSiegeService() { return siegeService; }
    public BypassManager getBypassManager() { return bypassManager; }

    public HologramManager getHologramManager() { return hologramManager; }
    public VisualManager getVisualManager() { return visualManager; }
    public AutoAddManager getAutoAddManager() { return autoAddManager; }
    public MenuManager getMenuManager() { return menuManager; }
    public EffectManager getEffectManager() { return effectManager; }

    public org.qweyns.qweprotectstones.features.effect.EffectPurchaseManager getEffectPurchaseManager() { return effectPurchaseManager; }

    public org.qweyns.qweprotectstones.features.teleport.HomeWarmup getHomeWarmup() { return homeWarmup; }
    public NotificationManager getNotificationManager() { return notificationManager; }
    public PenaltyManager getPenaltyManager() { return penaltyManager; }
    public RegionRateLimiter getRateLimiter() { return rateLimiter; }
    public InviteManager getInviteManager() { return inviteManager; }
    public RegionExporter getRegionExporter() { return regionExporter; }
    public DynmapIntegration getDynmapIntegration() { return dynmapIntegration; }

    public org.qweyns.qweprotectstones.regions.protection.RegionMovementListener getRegionMovementListener() { return regionMovementListener; }

    public BlueMapIntegration getBlueMapIntegration() { return blueMapIntegration; }

    public org.qweyns.qweprotectstones.features.market.MarketManager getMarketManager() { return marketManager; }

    public org.qweyns.qweprotectstones.features.backup.BackupTask getBackupTask() { return backupTask; }

    private void initMetrics() {
        if (!getConfigManager().getConfig().getBoolean("metrics.enable", true)) return;
        int pluginId = getConfigManager().getConfig().getInt("metrics.plugin-id", 0);
        if (pluginId <= 0) return;

        org.bstats.bukkit.Metrics metrics = new org.bstats.bukkit.Metrics(this, pluginId);
        metrics.addCustomChart(new org.bstats.charts.SingleLineChart("regions",
                () -> regionManager.size()));
        metrics.addCustomChart(new org.bstats.charts.AdvancedPie("region_types", () -> {
            java.util.Map<String, Integer> counts = new java.util.HashMap<>();
            for (Region region : regionManager.getAllRegions()) {
                counts.merge(region.getTypeId(), 1, Integer::sum);
            }
            return counts;
        }));
        getLogger().info("Метрики bStats включены (id " + pluginId + ").");
    }

    public org.qweyns.qweprotectstones.features.log.CriticalFileLogger getCriticalFileLogger() { return criticalFileLogger; }

    public org.qweyns.qweprotectstones.hooks.DiscordSrvHook getDiscordSrvHook() { return discordSrvHook; }
    public AbandonedRegionTask getAbandonedRegionTask() { return abandonedRegionTask; }

    public boolean isUnderSiege(org.qweyns.qweprotectstones.regions.Region region) {
        return region != null && region.isUnderSiege(tunables.siegeWindowMs());
    }

    public RegionLifecycleListener getRegionLifecycleListener() { return regionLifecycleListener; }

    public VaultHook getVaultHook() { return vaultHook; }
    public PlayerPointsHook getPlayerPointsHook() { return playerPointsHook; }

    public Region getRegionAt(org.bukkit.Location location) {
        return regionManager == null ? null : regionManager.getRegionAt(location);
    }
}
