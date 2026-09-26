package org.qweyns.qweprotectstones.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;

/** Отложенная очистка никогда не сносит ядро замещающего привата. */
public final class CoreBlocks {
    private CoreBlocks() { }
    public static void clearLater(QweProtectStones plugin, Region removed, Location core) {
        clearLater(plugin, removed, core, null);
    }

    public static void clearLater(QweProtectStones plugin, Region removed, Location core, org.bukkit.inventory.ItemStack drop) {
        if (core == null) return;
        var type = plugin.getRegionTypes().byId(removed.getTypeId());
        if (type == null) return;
        Material expected = type.material();
        Location at = core.clone();
        plugin.getSchedulers().runAtLocationLater(at, () -> {
            Region replacement = plugin.getRegionManager().getRegionAt(at);
            if (plugin.getRegionManager().getById(removed.getId()) != null
                    || replacement != null && replacement.isCore(at)) return;
            if (at.getWorld() != null && at.getBlock().getType() == expected) {
                at.getBlock().setType(Material.AIR);
                if (drop != null) at.getWorld().dropItemNaturally(at, drop);
            }
        }, 1L);
    }
}
