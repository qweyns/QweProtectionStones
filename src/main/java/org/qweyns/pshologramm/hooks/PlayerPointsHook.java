package org.qweyns.pshologramm.hooks;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PlayerPointsHook {
    private PlayerPointsAPI ppAPI = null;
    private boolean enabled = false;

    public void setup() {
        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
            ppAPI = PlayerPoints.getInstance().getAPI();
            enabled = true;
        }
    }

    public boolean isEnabled() { return enabled; }

    public boolean hasPoints(Player player, int amount) {
        return enabled && ppAPI.look(player.getUniqueId()) >= amount;
    }

    public void takePoints(Player player, int amount) {
        if (enabled) ppAPI.take(player.getUniqueId(), amount);
    }
}
