package org.qweyns.qweprotectstones.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.config.Tunables;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionManager;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.TrustLevel;
import org.qweyns.qweprotectstones.regions.event.RegionDeleteEvent;
import org.qweyns.qweprotectstones.regions.protection.ProtectionService;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Публичный API для сторонних плагинов.
 *
 * <p>Получение: {@code QpsApi.get()}. Инициализируется при включении плагина
 * и обнуляется при выключении — проверяйте {@link #isAvailable()} в коде,
 * который выполняется в момент выключения сервера.</p>
 *
 * <p>Все методы потокобезопасны настолько же, насколько и сам плагин:
 * геометрические запросы можно звать из любого потока, а создание и удаление
 * приватов — только из основного потока сервера (или через планировщик
 * вашего плагина).</p>
 */
public final class QpsApi {

    private static volatile QpsApi instance;

    private final QweProtectStones plugin;

    private QpsApi(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    /** Вызывается из onEnable. Повторный вызов перезаписывает экземпляр. */
    public static void init(QweProtectStones plugin) {
        instance = new QpsApi(plugin);
    }

    /** Вызывается из onDisable. */
    public static void shutdown() {
        instance = null;
    }

    /** Единственный экземпляр API или {@code null}, если плагин выключен. */
    public static QpsApi get() {
        return instance;
    }

    /** Жив ли плагин (удобно вызывать до {@link #get()}). */
    public static boolean isAvailable() {
        return instance != null;
    }

    // ------------------------------------------------------------------
    // Запросы
    // ------------------------------------------------------------------

    /** Приват в точке или {@code null}. */
    public Region getRegionAt(Location location) {
        return plugin.getRegionManager().getRegionAt(location);
    }

    /** Приват по полному UUID или {@code null}. */
    public Region getRegion(UUID regionId) {
        return plugin.getRegionManager().getById(regionId);
    }

    /** Приват по короткому id, который видят игроки ({@code /ps info}), или {@code null}. */
    public Region getRegionByShortId(String shortId) {
        return plugin.getRegionManager().getByShortId(shortId);
    }

    /** Все приваты владельца. */
    public List<Region> getRegionsOf(UUID ownerUuid) {
        return plugin.getRegionManager().getRegionsOf(ownerUuid);
    }

    /** Приваты, доступные игроку: свои плюс те, куда его вписали. */
    public List<Region> getAccessibleRegions(UUID playerUuid) {
        return plugin.getRegionManager().getAccessibleRegions(playerUuid);
    }

    /** Всего приватов на сервере. */
    public int getRegionCount() {
        return plugin.getRegionManager().size();
    }

    /** Все приваты — снимок для чтения. */
    public Collection<Region> getAllRegions() {
        return plugin.getRegionManager().getAllRegions();
    }

    // ------------------------------------------------------------------
    // Доверие и доступ
    // ------------------------------------------------------------------

    /** Уровень доступа игрока в привате или {@code null}, если он посторонний. */
    public TrustLevel trustOf(Region region, Player player) {
        return plugin.getProtectionService().trustOf(region, player);
    }

    /**
     * Достаточно ли у игрока доступа для действия.
     * Требуемый уровень берётся из {@code trust.required.*} — как у самого плагина.
     */
    public boolean isTrusted(Player player, Location location, Tunables.TrustAction action) {
        return plugin.getProtectionService().allows(player, location, action);
    }

    /** То же с явно заданным уровнем. */
    public boolean isTrusted(Player player, Location location, TrustLevel required) {
        return plugin.getProtectionService().allows(player, location, required);
    }

    /** Значение флага в точке (вне приватов все флаги разрешающие). */
    public boolean flagAt(Location location, org.qweyns.qweprotectstones.regions.RegionFlag flag) {
        return plugin.getProtectionService().flagAt(location, flag);
    }

    /** Идёт ли осада: приват атаковали недавно. */
    public boolean isUnderSiege(Region region) {
        return plugin.isUnderSiege(region);
    }

    // ------------------------------------------------------------------
    // Осада и взрывы (для аддонов кастомной взрывчатки)
    // ------------------------------------------------------------------

    /**
     * Сервис осады: снятие прочности привата взрывом с полным конвейером
     * плагина (кулдаун, RegionDamageEvent, алерты, уничтожение на нуле).
     * Подробности и примеры — docs/DYNAMITE_ADDON_API.md.
     */
    public SiegeService getSiegeService() {
        return plugin.getSiegeService();
    }

    /**
     * Снять прочность привата — как это делает сам плагин при взрыве.
     * Звать в потоке сервера.
     *
     * @param damage        единиц прочности (Разрывная волна сносит сразу 2)
     * @param explosionType тип взрыва для правил explosions у типа привата
     * @param attackerName  имя атакующего для статистики и алертов, может быть null
     * @return false — урон не прошёл (иммунитет, кулдаун, отмена RegionDamageEvent)
     */
    public boolean damageRegion(Region region, int damage, String explosionType, String attackerName) {
        return plugin.getSiegeService().damageRegion(region, damage, explosionType, attackerName);
    }

    /** Вредит ли взрыв данного типа прочности этого привата (правила explosions + raid_immune). */
    public boolean isExplosionDamaging(Region region, String explosionType) {
        return plugin.getSiegeService().isDamaging(region, explosionType);
    }

    /** Включена ли осада (siege.enabled). */
    public boolean isSiegeEnabled() {
        return plugin.getConfigManager().isSiegeEnabled();
    }

    // ------------------------------------------------------------------
    // Типы приватов
    // ------------------------------------------------------------------

    /** Тип привата по id или null. */
    public RegionType getRegionType(String id) {
        return id == null ? null : plugin.getRegionTypes().byId(id);
    }

    /** Тип текущего привата или null (тип мог удалиться из конфига). */
    public RegionType regionTypeOf(Region region) {
        return region == null ? null : plugin.getRegionTypes().byId(region.getTypeId());
    }

    /** Все зарегистрированные типы приватов. */
    public Collection<RegionType> getRegionTypes() {
        return plugin.getRegionTypes().all();
    }

    // ------------------------------------------------------------------
    // Управление (только основной поток сервера)
    // ------------------------------------------------------------------

    /** Создать приват от имени игрока; все проверки и лимиты — как при установке блока. */
    public RegionManager.CreateResult createRegion(Player owner, RegionType type, Location coreLocation) {
        return plugin.getRegionManager().createRegion(owner, type, coreLocation);
    }

    /** Удалить приват. {@code actor} может быть {@code null}. */
    public boolean deleteRegion(Region region, RegionDeleteEvent.Reason reason, Player actor) {
        return plugin.getRegionManager().deleteRegion(region, reason, actor);
    }

    /** Передать приват другому владельцу (прежний становится управляющим). */
    public void transferRegion(Region region, UUID newOwnerId, String newOwnerName) {
        plugin.getRegionManager().transferRegion(region, newOwnerId, newOwnerName);
    }

    /** Сервис защиты — для нестандартных проверок. */
    public ProtectionService getProtectionService() {
        return plugin.getProtectionService();
    }
}
