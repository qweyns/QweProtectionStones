package org.qweyns.qweprotectstones.hooks;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {
    private Economy economy;

    public void setup() {
        economy();
    }

    // Vault регистрирует Economy не раньше своего экономического плагина —
    // резолв повторяем при каждом обращении, пока не появится
    private Economy economy() {
        if (economy != null) return economy;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null;

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) economy = rsp.getProvider();
        return economy;
    }

    public boolean isEnabled() { return economy() != null; }

    public boolean hasMoney(Player player, double amount) {
        Economy e = economy();
        return e != null && (amount <= 0 || e.has(player, round(amount)));
    }

    public boolean takeMoney(Player player, double amount) {
        Economy e = economy();
        if (e == null) return false;
        if (amount <= 0) return true; // платить нечего — считаем успехом

        EconomyResponse response = e.withdrawPlayer(player, round(amount));
        return response != null && response.transactionSuccess();
    }

    public boolean giveMoney(OfflinePlayer player, double amount) {
        if (player == null) return false;
        Economy e = economy();
        if (e == null) return false;
        if (amount <= 0) return true;

        EconomyResponse response = e.depositPlayer(player, round(amount));
        return response != null && response.transactionSuccess();
    }

    // копейки от подстановок не должны расходиться с отображаемой ценой
    private static double round(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
}
