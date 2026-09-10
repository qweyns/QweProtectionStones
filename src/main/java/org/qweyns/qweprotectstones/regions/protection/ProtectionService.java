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

/**
 * Единая точка принятия решений «можно или нельзя». Все листенеры защиты
 * спрашивают только её, поэтому правила описаны в одном месте, а не размазаны
 * по двадцати обработчикам.
 */
public class ProtectionService {

    public static final String BYPASS_PERMISSION = "qweprotectstones.bypass";
    public static final String ADMIN_PERMISSION = "qweprotectstones.admin";

    private final QweProtectStones plugin;
    private final Cache<UUID, Long> denyMessageCooldowns = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();

    public ProtectionService(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Базовые проверки
    // ------------------------------------------------------------------

    public Region regionAt(Location location) {
        return plugin.getRegionManager().getRegionAt(location);
    }

    /**
     * Игнорирует ли игрок защиту. Режим обхода включается командой и
     * требует права, поэтому обычный админ не ломает чужие постройки случайно.
     */
    public boolean bypasses(Player player) {
        return player != null && player.hasPermission(BYPASS_PERMISSION) && plugin.getBypassManager().isEnabled(player);
    }

    /** Уровень доступа игрока с учётом бана и флага публичного доступа. */
    public TrustLevel trustOf(Region region, Player player) {
        if (region == null || player == null) return null;
        if (bypasses(player)) return TrustLevel.OWNER;

        // Бан сильнее любых прав: даже публичный приват забаненного не пускает.
        if (region.isBanned(player.getUniqueId())) return null;

        TrustLevel explicit = region.getTrust(player.getUniqueId());
        if (explicit != null) return explicit;

        // Публичный приват пускает всех на уровень «пользоваться, но не ломать».
        return flag(region, RegionFlag.PUBLIC_ACCESS) ? TrustLevel.CONTAINER : null;
    }

    /** Забанен ли игрок в этом привате (админский обход бан игнорирует). */
    public boolean isBanned(Region region, Player player) {
        return region != null && player != null && !bypasses(player) && region.isBanned(player.getUniqueId());
    }

    public boolean has(Region region, Player player, TrustLevel required) {
        TrustLevel trust = trustOf(region, player);
        return trust != null && trust.atLeast(required);
    }

    /** Разрешено ли действие требуемого уровня в этой точке. */
    public boolean allows(Player player, Location location, TrustLevel required) {
        Region region = regionAt(location);
        return region == null || has(region, player, required);
    }

    /**
     * Уровень, который сервер требует для действия. Значения настраиваются в
     * {@code trust.required.*}, поэтому «пускать к сундукам всех, кому дан
     * ACCESS» решается конфигом, а не правкой кода.
     */
    public TrustLevel requiredFor(Tunables.TrustAction action) {
        return plugin.getTunables().required(action);
    }

    public boolean allows(Player player, Location location, Tunables.TrustAction action) {
        return allows(player, location, requiredFor(action));
    }

    public boolean canManage(Player player, Region region) {
        return region != null && has(region, player, requiredFor(Tunables.TrustAction.MANAGE));
    }

    /** Может ли игрок менять флаги: уровень настраивается отдельно от прочего управления. */
    public boolean canEditFlags(Player player, Region region) {
        return region != null && has(region, player, plugin.getTunables().flagEditLevel());
    }

    /** Может ли игрок выдавать и отзывать доступ другим. */
    public boolean canEditMembers(Player player, Region region) {
        return region != null && has(region, player, plugin.getTunables().memberEditLevel());
    }

    // ------------------------------------------------------------------
    // Флаги
    // ------------------------------------------------------------------

    /**
     * Значение флага: переопределение привата → настройка типа → значение по
     * умолчанию у самого флага.
     */
    public boolean flag(Region region, RegionFlag flag) {
        if (region == null) return true;

        Boolean override = region.getFlagOverrides().get(flag);
        if (override != null) return override;

        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        return type != null ? type.flagDefault(flag) : flag.defaultValue();
    }

    /** Значение флага в точке. Вне приватов флаги считаются разрешающими. */
    public boolean flagAt(Location location, RegionFlag flag) {
        return flag(regionAt(location), flag);
    }

    // ------------------------------------------------------------------
    // Сообщения
    // ------------------------------------------------------------------

    /** Показывает отказ в actionbar, не чаще раза в две секунды на игрока. */
    public void notifyDenied(Player player, Region region) {
        if (player == null) return;

        Long last = denyMessageCooldowns.getIfPresent(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < plugin.getTunables().denyMessageCooldownMs()) return;
        denyMessageCooldowns.put(player.getUniqueId(), now);

        String owner = region != null ? region.getOwnerName() : "";
        player.sendActionBar(plugin.getLanguageManager().getMessage("protection_denied", "%owner%", owner));
    }

    /** Комбинация «проверить и, если нельзя, сообщить». Уровень берётся из trust.required.build. */
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
