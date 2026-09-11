package org.qweyns.qweprotectstones.hooks;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

public class PlayerPointsHook {
    private PlayerPointsAPI api;

    public void setup(Logger logger) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlayerPoints")) return;

        try {
            PlayerPoints instance = PlayerPoints.getInstance();
            if (instance != null) api = instance.getAPI();
        } catch (Throwable t) {

            logger.warning("Не удалось подключиться к PlayerPoints: " + t.getMessage());
        }
    }

    public boolean isEnabled() { return api != null; }

    public boolean hasPoints(Player player, int amount) {
        return api != null && api.look(player.getUniqueId()) >= amount;
    }

    public boolean takePoints(Player player, int amount) {
        if (api == null || amount <= 0) return false;
        return api.take(player.getUniqueId(), amount);
    }
}
