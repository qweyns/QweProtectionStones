package org.qweyns.qweprotectstones.holograms;

import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;

/** Подстановка данных привата в строки голограммы. */
final class HologramText {

    private HologramText() {
    }

    static String apply(QweProtectStones plugin, String line, Region region) {
        if (line == null) return "";

        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());

        return line.replace("%player%", region.getOwnerName())
                // Название типа (display_name из regions.yml): чтобы не
                // вписывать его дважды — и в display_name, и в строки голограммы.
                .replace("%type%", type == null ? region.getTypeId() : type.displayName())
                .replace("%owner%", region.getOwnerName())
                .replace("%name%", region.getLabel())
                .replace("%durability%", String.valueOf(region.getDurability()))
                .replace("%max_durability%", String.valueOf(region.getMaxDurability()))
                .replace("%members%", String.valueOf(region.getMemberCount()))
                .replace("%size%", type == null ? "?" : type.widthX() + "x" + type.widthZ())
                .replace("%id%", region.getShortId())
                .replace("%item%", plugin.getMenuManager().upgradeItemFor(region).name())
                .replace("%siege%", plugin.getLanguageManager()
                        .rawTemplate(plugin.isUnderSiege(region) ? "siege_active" : "siege_calm"))
                .replace("%penalty%", plugin.getPenaltyManager().hasPenalty(region.getId())
                        ? String.valueOf(plugin.getPenaltyManager().getPenaltyMultiplier())
                        : plugin.getLanguageManager().rawTemplate("no_penalty"))
                .replace("%attacks%", String.valueOf(region.getAttackCount()));
    }
}
