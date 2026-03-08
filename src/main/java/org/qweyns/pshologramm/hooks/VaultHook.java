package org.qweyns.pshologramm.hooks;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {
    private Economy econ = null;
    private boolean enabled = false;

    public void setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                econ = rsp.getProvider();
                enabled = true;
            }
        }
    }

    public boolean isEnabled() { return enabled; }

    public boolean hasMoney(Player player, double amount) {
        return enabled && econ.has(player, amount);
    }

    public void takeMoney(Player player, double amount) {
        if (enabled) econ.withdrawPlayer(player, amount);
    }
}
