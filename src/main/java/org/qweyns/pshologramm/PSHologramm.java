package org.qweyns.pshologramm;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.qweyns.pshologramm.config.ConfigManager;
import org.qweyns.pshologramm.config.LanguageManager;
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

import java.util.Collections;
import java.util.List;

public final class PSHologramm extends JavaPlugin {

  private ConfigManager configManager;
  private LanguageManager languageManager;
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

    this.languageManager = new LanguageManager(this);
    this.languageManager.init();

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
      ApiCommandExecutor apiExec = new ApiCommandExecutor(this);
      getCommand("psholo-api").setExecutor(apiExec);
      getCommand("psholo-api").setTabCompleter(apiExec);
    }

    getLogger().info("PSHologramm v1.1.1 - успешно запущен");
  }

  @Override
  public void onDisable() {
    for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
      org.bukkit.inventory.Inventory topInv = player.getOpenInventory().getTopInventory();
      if (topInv != null && topInv.getHolder() instanceof org.qweyns.pshologramm.menus.MenuManager.CustomHolder) {
        player.closeInventory();
      }
    }

    if (autoAddManager != null) autoAddManager.saveAllOnline();

    if (hologramManager != null) hologramManager.deleteAll();
    if (storageManager != null) storageManager.close();
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (command.getName().equalsIgnoreCase("pshologramm") && args.length == 1 && args[0].equalsIgnoreCase("reload")) {
      if (!sender.hasPermission("pshologramm.admin")) {
        sender.sendMessage(languageManager.getMessage("no_permission"));
        return true;
      }

      configManager.reload();
      languageManager.init();
      menuManager.loadMenus();

      hologramManager.deleteAll();
      hologramManager.restoreHolograms();

      sender.sendMessage(languageManager.getMessage("reload_success"));
      return true;
    }
    return false;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (command.getName().equalsIgnoreCase("pshologramm") && args.length == 1) {
      if (sender.hasPermission("pshologramm.admin")) {
        return Collections.singletonList("reload");
      }
    }
    return null;
  }

  public ConfigManager getConfigManager() { return configManager; }
  public LanguageManager getLanguageManager() { return languageManager; }
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
