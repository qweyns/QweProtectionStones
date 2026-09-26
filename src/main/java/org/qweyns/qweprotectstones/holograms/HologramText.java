package org.qweyns.qweprotectstones.holograms;

import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;

final class HologramText {

    // NETHERITE_INGOT -> Netherite Ingot: служебное имя в голограмме ни к чему
    private static String prettyItem(org.bukkit.Material material) {
        String[] parts = material.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private HologramText() {
    }

    static String apply(QweProtectStones plugin, String line, Region region) {
        if (line == null) return "";

        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());

        return line.replace("%player%", region.getOwnerName())

                .replace("%type%", type == null ? region.getTypeId() : type.displayName())
                .replace("%owner%", region.getOwnerName())
                .replace("%name%", region.getLabel())
                .replace("%durability%", String.valueOf(region.getDurability()))
                .replace("%max_durability%", String.valueOf(region.getMaxDurability()))
                .replace("%members%", String.valueOf(region.getMemberCount()))
                .replace("%size%", type == null ? "?" : type.widthX() + "x" + type.widthZ())
                .replace("%id%", region.getShortId())
                .replace("%item%", prettyItem(plugin.getMenuManager().upgradeItemFor(region)))
                .replace("%siege%", plugin.getLanguageManager()
                        .rawTemplate(plugin.isUnderSiege(region) ? "siege_active" : "siege_calm"))
                .replace("%penalty%", plugin.getPenaltyManager().hasPenalty(region)
                        ? String.valueOf(plugin.getPenaltyManager().getPenaltyMultiplier())
                        : plugin.getLanguageManager().rawTemplate("no_penalty"))
                .replace("%attacks%", String.valueOf(region.getAttackCount()));
    }
}
