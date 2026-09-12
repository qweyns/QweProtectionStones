package org.qweyns.qweprotectstones.regions.protection;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.TrustLevel;
import org.qweyns.qweprotectstones.config.Tunables;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ProtectionService {

    // отдельное право, объявлено в plugin.yml; право админа — только через ConfigManager.getAdminPermissionPrefix()
    public static final String BYPASS_PERMISSION = "qweprotectstones.bypass";

    private final QweProtectStones plugin;
    private Cache<UUID, Long> denyMessageCooldowns = CacheBuilder.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS).build();

    public ProtectionService(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public void reloadDenyCooldown() {
        denyMessageCooldowns = CacheBuilder.newBuilder()
                .expireAfterWrite(Math.max(1, plugin.getTunables().denyMessageCooldownMs() * 2),
                        TimeUnit.MILLISECONDS)
                .build();
    }

    public Region regionAt(Location location) {
        return plugin.getRegionManager().getRegionAt(location);
    }

    public boolean bypasses(Player player) {
        return player != null && player.hasPermission(BYPASS_PERMISSION) && plugin.getBypassManager().isEnabled(player);
    }

    public TrustLevel trustOf(Region region, Player player) {
        if (region == null || player == null) return null;
        if (bypasses(player)) return TrustLevel.OWNER;

        // бан сильнее всех прав
        if (region.isBanned(player.getUniqueId())) return null;

        TrustLevel explicit = region.getTrust(player.getUniqueId());
        if (explicit != null) return explicit;

        // публичный: пользоваться можно, ломать нельзя
        return flag(region, RegionFlag.PUBLIC_ACCESS) ? TrustLevel.CONTAINER : null;
    }

    public boolean isBanned(Region region, Player player) {
        return region != null && player != null && !bypasses(player) && region.isBanned(player.getUniqueId());
    }

    public boolean has(Region region, Player player, TrustLevel required) {
        TrustLevel trust = trustOf(region, player);
        return trust != null && trust.atLeast(required);
    }

    public boolean allows(Player player, Location location, TrustLevel required) {
        Region region = regionAt(location);
        return region == null || has(region, player, required);
    }

    public TrustLevel requiredFor(Tunables.TrustAction action) {
        return plugin.getTunables().required(action);
    }

    public boolean allows(Player player, Location location, Tunables.TrustAction action) {
        return allows(player, location, requiredFor(action));
    }

    public boolean canManage(Player player, Region region) {
        return region != null && has(region, player, requiredFor(Tunables.TrustAction.MANAGE));
    }

    public boolean flag(Region region, RegionFlag flag) {
        if (region == null) return true;

        Boolean override = region.getFlagOverrides().get(flag);
        if (override != null) return override;

        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        return type != null ? type.flagDefault(flag) : flag.defaultValue();
    }

    public boolean flagAt(Location location, RegionFlag flag) {
        return flag(regionAt(location), flag);
    }

    public void notifyDenied(Player player, Region region, String verbKey) {
        if (player == null) return;

        Long last = denyMessageCooldowns.getIfPresent(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < plugin.getTunables().denyMessageCooldownMs()) return;
        denyMessageCooldowns.put(player.getUniqueId(), now);

        // стиль WorldGuard: короткое «Эй!» и причина — но в чат
        player.sendMessage(plugin.getLanguageManager().getMessage("protection_denied",
                "%what%", plugin.getLanguageManager().rawTemplate("deny_verb_" + verbKey)));
    }

    public void notifyDenied(Player player, Region region) {
        notifyDenied(player, region, "build");
    }

    public boolean denyBuild(Player player, Location location) {
        Region region = regionAt(location);
        if (region == null || has(region, player, requiredFor(Tunables.TrustAction.BUILD))) return false;

        notifyDenied(player, region);
        return true;
    }

    public boolean denyInteract(Player player, Location location, TrustLevel required) {
        Region region = regionAt(location);
        if (region == null || has(region, player, required)) return false;

        notifyDenied(player, region);
        return true;
    }
}
