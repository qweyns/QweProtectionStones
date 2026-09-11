package org.qweyns.qweprotectstones.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.util.Locale;

public class PAPIExpansion extends PlaceholderExpansion {

    private final QweProtectStones plugin;

    public PAPIExpansion(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() { return "qps"; }

    @Override
    public @NotNull String getAuthor() { return "Qweyns"; }

    @Override
    public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }

    /** Без этого PlaceholderAPI выгружает расширение при /papi reload. */
    @Override
    public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        String key = params.toLowerCase(Locale.ROOT);

        // Плейсхолдеры, не зависящие от текущей позиции игрока.
        switch (key) {
            case "regions_count" -> {
                return String.valueOf(plugin.getRegionManager().getRegionsOf(player.getUniqueId()).size());
            }
            case "regions_total" -> {
                return String.valueOf(plugin.getRegionManager().size());
            }
            case "regions_area" -> {
                // Суммарная охраняемая площадь всех приватов игрока (в блоках).
                long area = 0;
                for (Region owned : plugin.getRegionManager().getRegionsOf(player.getUniqueId())) {
                    RegionType type = plugin.getRegionTypes().byId(owned.getTypeId());
                    if (type != null) area += (long) type.widthX() * type.widthZ();
                }
                return String.valueOf(area);
            }
            default -> { /* дальше смотрим на приват под ногами */ }
        }

        Region region = plugin.getRegionManager().getRegionAt(player.getLocation());
        if (region == null) return plugin.getLanguageManager().getRawMessage("papi_no_region");

        return switch (key) {
            case "standing_durability" -> String.valueOf(region.getDurability());
            case "standing_max_durability" -> String.valueOf(region.getMaxDurability());
            case "standing_owner" -> region.getOwnerName();
            case "standing_name" -> region.getLabel();
            case "standing_type" -> region.getTypeId();
            case "standing_id" -> region.getShortId();
            case "standing_members" -> String.valueOf(region.getMemberCount());
            case "standing_size" -> {
                RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
                yield type == null ? "?" : type.widthX() + "x" + type.widthZ();
            }
            case "standing_trust" -> {
                TrustLevel trust = plugin.getProtectionService().trustOf(region, player);
                yield plugin.getLanguageManager().getRawMessage(trust == null ? "trust_none" : "trust_" + trust.key());
            }
            case "standing_penalty" -> plugin.getPenaltyManager().hasPenalty(region.getId())
                    ? String.valueOf(plugin.getPenaltyManager().getPenaltyMultiplier())
                    : "0";
            case "standing_siege" -> plugin.getLanguageManager()
                    .getRawMessage(plugin.isUnderSiege(region) ? "siege_active" : "siege_calm");
            case "standing_attacks" -> String.valueOf(region.getAttackCount());
            case "standing_last_attacker" -> region.getLastAttackerName();
            case "standing_unseen_attacks" -> String.valueOf(region.getUnseenAttacks());
            default -> null;
        };
    }
}
