package org.qweyns.pshologramm.hooks;

import dev.espi.protectionstones.PSRegion;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.qweyns.pshologramm.PSHologramm;
import org.qweyns.pshologramm.models.RegionData;

public class PAPIExpansion extends PlaceholderExpansion {
    private final PSHologramm plugin;

    public PAPIExpansion(PSHologramm plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() { return "psholo"; }

    @Override
    public @NotNull String getAuthor() { return "Qweyns"; }

    @Override
    public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        PSRegion region = PSRegion.fromLocation(player.getLocation());
        if (region == null) return "Нет привата";

        RegionData rd = plugin.getStorageManager().getRegion(region.getId());
        if (rd == null) return "Загрузка...";

        if (params.equalsIgnoreCase("standing_durability")) {
            return String.valueOf(rd.getDurability());
        }
        if (params.equalsIgnoreCase("standing_max_durability")) {
            return String.valueOf(rd.getMaxDurability());
        }
        if (params.equalsIgnoreCase("standing_penalty")) {
            return plugin.getPenaltyManager().hasPenalty(rd.getId()) ?
                    String.valueOf(plugin.getPenaltyManager().getPenaltyMultiplier()) : "0";
        }

        return null;
    }
}
