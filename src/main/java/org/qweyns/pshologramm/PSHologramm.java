package org.qweyns.pshologramm;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.qweyns.pshologramm.config.ConfigManager;
import org.qweyns.pshologramm.features.autoadd.AutoAddManager;
import org.qweyns.pshologramm.features.effect.EffectManager;
import org.qweyns.pshologramm.features.effect.ExpBoostListener;
import org.qweyns.pshologramm.features.notification.NotificationManager;
import org.qweyns.pshologramm.features.penalty.PenaltyManager;
import org.qweyns.pshologramm.features.visual.VisualManager;
import org.qweyns.pshologramm.holograms.HologramManager;
import org.qweyns.pshologramm.hooks.PAPIExpansion;
import org.qweyns.pshologramm.hooks.PlayerPointsHook;
import org.qweyns.pshologramm.hooks.VaultHook;
import org.qweyns.pshologramm.listeners.ApiCommandExecutor;
import org.qweyns.pshologramm.listeners.CommandInterceptor;
import org.qweyns.pshologramm.listeners.RegionExplosionListener;
import org.qweyns.pshologramm.listeners.RegionInteractListener;
import org.qweyns.pshologramm.listeners.RegionLifecycleListener;
import org.qweyns.pshologramm.menus.MenuManager;
import org.qweyns.pshologramm.storage.StorageManager;

public final class PSHologramm extends JavaPlugin {

    private ConfigManager configManager;
    private StorageManager storageManager;
    private HologramManager hologramManager;
    private VisualManager visualManager;
    private AutoAddManager autoAddManager;
    private MenuManager menuManager;
    private EffectManager effectManager;
    private NotificationManager notificationManager;
    private PenaltyManager penaltyManager;

    private VaultHook vaultHook;
    private PlayerPointsHook playerPointsHook;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.storageManager = new StorageManager(this);
        this.storageManager.init();

        this.vaultHook = new VaultHook();
        this.vaultHook.setup();
        this.playerPointsHook = new PlayerPointsHook();
        this.playerPointsHook.setup();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PAPIExpansion(this).register();
        }

        this.visualManager = new VisualManager(this);
        this.autoAddManager = new AutoAddManager(this);
        this.effectManager = new EffectManager(this);
        this.notificationManager = new NotificationManager(this);
        this.penaltyManager = new PenaltyManager(this);
        this.menuManager = new MenuManager(this);

        this.hologramManager = new HologramManager(this);
        this.hologramManager.init();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new RegionLifecycleListener(this), this);
        pm.registerEvents(new RegionExplosionListener(this), this);
        pm.registerEvents(new RegionInteractListener(this), this);
        pm.registerEvents(new CommandInterceptor(this), this);
        pm.registerEvents(new ExpBoostListener(this), this);

        if (getCommand("psholo-api") != null) {
            getCommand("psholo-api").setExecutor(new ApiCommandExecutor(this));
        }

        getLogger().info("PSHologramm v1.4.0 успешно запущен (API Edition)!");
    }

    @Override
    public void onDisable() {
        if (hologramManager != null) hologramManager.deleteAll();
        if (storageManager != null) storageManager.close();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("pshologramm") && args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("pshologramm.admin")) {
                sender.sendMessage(configManager.getMessage("no_permission"));
                return true;
            }
            configManager.reload();
            menuManager.loadMenus();
            hologramManager.deleteAll();
            hologramManager.restoreHolograms();
            sender.sendMessage(configManager.getMessage("reload_success"));
            return true;
        }
        return false;
    }

    public ConfigManager getConfigManager() { return configManager; }
    public StorageManager getStorageManager() { return storageManager; }
    public HologramManager getHologramManager() { return hologramManager; }
    public VisualManager getVisualManager() { return visualManager; }
    public AutoAddManager getAutoAddManager() { return autoAddManager; }
    public MenuManager getMenuManager() { return menuManager; }
    public EffectManager getEffectManager() { return effectManager; }
    public NotificationManager getNotificationManager() { return notificationManager; }
    public PenaltyManager getPenaltyManager() { return penaltyManager; }

    public VaultHook getVaultHook() { return vaultHook; }
    public PlayerPointsHook getPlayerPointsHook() { return playerPointsHook; }
}
