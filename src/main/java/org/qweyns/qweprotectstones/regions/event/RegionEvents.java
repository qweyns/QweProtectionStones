package org.qweyns.qweprotectstones.regions.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.util.UUID;

public final class RegionEvents {

    private RegionEvents() {
    }

    public static boolean fireFlagChange(Region region, Player player, RegionFlag flag, Boolean newValue) {
        Boolean oldValue = region.getFlagOverride(flag).orElse(null);
        return call(new RegionFlagChangeEvent(region, player, flag, oldValue, newValue));
    }

    public static boolean fireMemberChange(Region region, Player actor, UUID targetUuid, String targetName,
                                           RegionMemberChangeEvent.Action action, TrustLevel trust) {
        return call(new RegionMemberChangeEvent(region, actor, targetUuid, targetName, action, trust));
    }

    public static boolean fireTransfer(Region region, Player actor,
                                       UUID newOwnerId, String newOwnerName) {
        return call(new RegionTransferEvent(region, actor,
                region.getOwnerId(), region.getOwnerName(), newOwnerId, newOwnerName));
    }

    public static boolean fireEffectPurchase(Region region, Player player, String effectName, int amplifier) {
        return call(new EffectPurchaseEvent(region, player, effectName, amplifier));
    }

    private static boolean call(org.bukkit.event.Event event) {
        Bukkit.getPluginManager().callEvent(event);
        return event instanceof Cancellable cancellable && cancellable.isCancelled();
    }
}
