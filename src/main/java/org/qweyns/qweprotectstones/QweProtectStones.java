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

    /** Префикс прав плагина; узлы объявлены в plugin.yml. */
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
    private BlueMapIntegration blueMapIntegration;
    private org.qweyns.qweprotectstones.features.market.MarketManager marketManager;
    private org.qweyns.qweprotectstones.features.backup.BackupTask backupTask;
    private org.qweyns.qweprotectstones.hooks.DiscordSrvHook discordSrvHook;
    private AbandonedRegionTask abandonedRegionTask;
    private RegionActionLogger actionLogger;
    private org.qweyns.qweprotectstones.features.log.CriticalFileLogger criticalFileLogger;
    private RegionPreviewListener previewListener;
    private org.qweyns.qweprotectstones.features.recipe.CoreRecipeManager coreRecipeManager;

    private RegionLifecycleListener regionLifecycleListener;
    private RegionMovementListener regionMovementListener;
    private RegionCommand regionCommand;

    private VaultHook vaultHook;
    private PlayerPointsHook playerPointsHook;

    @Override
    public void onEnable() {
        // Планировщик выбирается первым: на нём держится вся остальная асинхронность.
        this.schedulers = Schedulers.create(this);
        this.configManager = new ConfigManager(this);
        this.regionConfig = new RegionConfig(this);
        this.tunables = new Tunables(this);

        this.languageManager = new LanguageManager(this);
        this.languageManager.init();

        // Типы приватов нужны раньше хранилища: по ним восстанавливаются приваты.
        this.regionTypes = new RegionTypeRegistry(this);
        this.regionTypes.load();
        this.coreRecipeManager = new org.qweyns.qweprotectstones.features.recipe.CoreRecipeManager(this);

        this.bypassManager = new BypassManager();
        this.rateLimiter = new RegionRateLimiter(this);
        this.protectionService = new ProtectionService(this);
        this.protectionService.reloadDenyCooldown();

        this.regionStorage = new RegionStorage(this);
        this.regionManager = new RegionManager(this);
        this.regionManager.loadAll(regionStorage.init());
        this.regionManager.refreshTypeData();
        // Крафты зависят от типов: регистрируем после загрузки regions.yml.
        this.coreRecipeManager.reload();

        this.vaultHook = new VaultHook();
        this.vaultHook.setup();
        this.playerPointsHook = new PlayerPointsHook();
        this.playerPointsHook.setup(getLogger());
        this.discordSrvHook = new org.qweyns.qweprotectstones.hooks.DiscordSrvHook(this);
        this.discordSrvHook.setup();

        // Рынок (продажа/аренда) грузит объявления сразу: без Vault просто
        // откажется проводить сделки.
        this.marketManager = new MarketManager(this);
        this.marketManager.load();

        PluginManager pm = getServer().getPluginManager();
        if (pm.isPluginEnabled("PlaceholderAPI") && getConfigManager().getConfig().getBoolean("placeholders.enabled", true)) {
            new PAPIExpansion(this).register();
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

        registerListeners(pm);
        registerCommands();

        this.abandonedRegionTask = new AbandonedRegionTask(this);
        this.abandonedRegionTask.start();
        this.marketManager.startExpiryTask();
        this.actionLogger.startPruning();

        // Плановые выгрузки всех приватов с ротацией (backup.enable).
        this.criticalFileLogger = new org.qweyns.qweprotectstones.features.log.CriticalFileLogger(this);
        this.backupTask = new org.qweyns.qweprotectstones.features.backup.BackupTask(this);
        this.backupTask.start();

        // Dynmap подключаем последним: к этому моменту приваты уже загружены.
        this.dynmapIntegration = new DynmapIntegration(this);
        this.dynmapIntegration.enable();

        // BlueMap — та же история: подписка на onEnable API, если карта грузится позже.
        this.blueMapIntegration = new BlueMapIntegration(this);
        this.blueMapIntegration.enable();

        getLogger().info("QweProtectStones v" + getPluginMeta().getVersion() + " запущен"
                + (schedulers.isFolia() ? " (режим Folia)" : "") + ": приватов — " + regionManager.size());

        // API для сторонних плагинов инициализируем последним: всё уже готово.
        QpsApi.init(this);
    }

    private void registerListeners(PluginManager pm) {
        this.regionLifecycleListener = new RegionLifecycleListener(this);
        this.regionMovementListener = new RegionMovementListener(this);

        pm.registerEvents(bypassManager, this);
        pm.registerEvents(regionLifecycleListener, this);
        pm.registerEvents(regionMovementListener, this);
        pm.registerEvents(new RegionExplosionListener(this), this);
        pm.registerEvents(new RegionInteractListener(this), this);
        pm.registerEvents(new ExpBoostListener(this), this);

        // Движок защиты: то, что раньше делал WorldGuard.
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

        // Задержка /ps home: слушает движение и урон, чтобы срывать телепорт.
        pm.registerEvents(homeWarmup, this);
    }

    private void registerCommands() {
        // Игровая команда: имя и алиасы задаются в config.yml, поэтому она
        // регистрируется в CommandMap, а не объявляется в plugin.yml.
        String name = configManager.getCommandName();
        List<String> aliases = configManager.getCommandAliases();

        this.regionCommand = new RegionCommand(this, name, aliases);
        if (CommandRegistrar.register(this, regionCommand)) {
            getLogger().info("Команда игроков: /" + name
                    + (aliases.isEmpty() ? "" : " (алиасы: " + String.join(", ", aliases) + ")"));
        }

        // Административная команда носит имя плагина и объявлена в plugin.yml.
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
        // API закрываем первым: сторонние плагины не должны получить NPE при выключении.
        QpsApi.shutdown();
        if (blueMapIntegration != null) blueMapIntegration.disable();

        // Меню закрываем первыми: InventoryCloseEvent снимет задачи анимации.
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory topInv = player.getOpenInventory().getTopInventory();
            if (topInv.getHolder() instanceof MenuHolder) player.closeInventory();
        }

        if (regionCommand != null) CommandRegistrar.unregister(this, regionCommand);
        if (visualManager != null) visualManager.shutdown();
        if (bypassManager != null) bypassManager.clear();
        if (rateLimiter != null) rateLimiter.clear();
        if (inviteManager != null) inviteManager.clear();
        if (previewListener != null) previewListener.clear();
        if (schedulers != null) schedulers.cancelAll();
        if (autoAddManager != null) autoAddManager.saveAllOnline();
        if (hologramManager != null) hologramManager.deleteAll();

        if (regionStorage != null) {
            // Полное сохранение: отложенная очередь могла не успеть сработать.
            if (regionManager != null) regionStorage.saveAll(regionManager.getAllRegions());
            regionStorage.close();
        }
    }

    /** Перезагрузка конфигов без перезапуска сервера. */
    public void reloadEverything() {
        configManager.reload();
        regionConfig.reload();
        tunables.reload();
        languageManager.init();
        regionTypes.load();
        regionManager.refreshTypeData();
        coreRecipeManager.reload();
        menuManager.loadMenus();
        penaltyManager.rebuild();
        protectionService.reloadDenyCooldown();

        hologramManager.restoreHolograms();
        if (dynmapIntegration != null) dynmapIntegration.redrawAll();
        // enable() идемпотентен: перечитывает настройки и перерисовывает маркеры.
        if (blueMapIntegration != null) blueMapIntegration.enable();

        // Имя команды меняется только после перезапуска — переучивать игроков
        // посреди сессии хуже, чем подождать рестарта.
        getLogger().info("Конфигурация перезагружена. Смена имени команды применится после перезапуска сервера.");
    }

    // ------------------------------------------------------------------
    // Доступ к подсистемам
    // ------------------------------------------------------------------

    public Schedulers getSchedulers() { return schedulers; }

    public ConfigManager getConfigManager() { return configManager; }

    /** Настройки типов приватов из regions.yml. */
    public RegionConfig getRegionConfig() { return regionConfig; }

    /** Тайминги, звуки, частицы и правила доступа, вычитанные из config.yml. */
    public Tunables getTunables() { return tunables; }
    public LanguageManager getLanguageManager() { return languageManager; }

    public RegionTypeRegistry getRegionTypes() { return regionTypes; }
    public RegionStorage getRegionStorage() { return regionStorage; }
    public RegionManager getRegionManager() { return regionManager; }
    public ProtectionService getProtectionService() { return protectionService; }
    public BypassManager getBypassManager() { return bypassManager; }

    public HologramManager getHologramManager() { return hologramManager; }
    public VisualManager getVisualManager() { return visualManager; }
    public AutoAddManager getAutoAddManager() { return autoAddManager; }
    public MenuManager getMenuManager() { return menuManager; }
    public EffectManager getEffectManager() { return effectManager; }

    /** Оплата эффектов: деньги, очки или предметы (effects.purchase). */
    public org.qweyns.qweprotectstones.features.effect.EffectPurchaseManager getEffectPurchaseManager() { return effectPurchaseManager; }

    /** Задержка и перезарядка телепорта /ps home. */
    public org.qweyns.qweprotectstones.features.teleport.HomeWarmup getHomeWarmup() { return homeWarmup; }
    public NotificationManager getNotificationManager() { return notificationManager; }
    public PenaltyManager getPenaltyManager() { return penaltyManager; }
    public RegionRateLimiter getRateLimiter() { return rateLimiter; }
    public InviteManager getInviteManager() { return inviteManager; }
    public RegionExporter getRegionExporter() { return regionExporter; }
    public DynmapIntegration getDynmapIntegration() { return dynmapIntegration; }

    /** Интеграция с BlueMap (может быть неактивна, если карта не установлена). */
    public BlueMapIntegration getBlueMapIntegration() { return blueMapIntegration; }

    /** Рынок приватов: продажа и аренда. */
    public org.qweyns.qweprotectstones.features.market.MarketManager getMarketManager() { return marketManager; }

    /** Плановые выгрузки приватов в JSON с ротацией. */
    public org.qweyns.qweprotectstones.features.backup.BackupTask getBackupTask() { return backupTask; }

    public org.qweyns.qweprotectstones.features.log.CriticalFileLogger getCriticalFileLogger() { return criticalFileLogger; }

    /** Уведомления через DiscordSRV (рефлексия, работает без зависимости). */
    public org.qweyns.qweprotectstones.hooks.DiscordSrvHook getDiscordSrvHook() { return discordSrvHook; }
    public AbandonedRegionTask getAbandonedRegionTask() { return abandonedRegionTask; }

    /** Идёт ли осада: приват атаковали недавно (окно берётся из настройки штрафа). */
    public boolean isUnderSiege(org.qweyns.qweprotectstones.regions.Region region) {
        return region != null && region.isUnderSiege(tunables.siegeWindowMs());
    }

    public RegionLifecycleListener getRegionLifecycleListener() { return regionLifecycleListener; }

    public VaultHook getVaultHook() { return vaultHook; }
    public PlayerPointsHook getPlayerPointsHook() { return playerPointsHook; }

    /** Удобный доступ для сторонних плагинов: приват в точке или null. */
    public Region getRegionAt(org.bukkit.Location location) {
        return regionManager == null ? null : regionManager.getRegionAt(location);
    }
}
