package org.qweyns.qweprotectstones.hooks;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {
    private Economy economy;

    public void setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) economy = rsp.getProvider();
    }

    public boolean isEnabled() { return economy != null; }

    public boolean hasMoney(Player player, double amount) {
        return economy != null && economy.has(player, amount);
    }

    /**
     * @return true, если деньги действительно списаны. Раньше результат
     * игнорировался, и покупка проходила даже при неудачной транзакции.
     */
    public boolean takeMoney(Player player, double amount) {
        if (economy == null || amount <= 0) return false;

        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }
}
