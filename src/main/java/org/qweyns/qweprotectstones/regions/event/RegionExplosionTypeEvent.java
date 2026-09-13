package org.qweyns.qweprotectstones.regions.event;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * QPS определяет тип взрыва перед расчётом урона осаде. Аддоны кастомной
 * взрывчатки (динамиты, С4, разрывная волна) слушают это событие и подменяют
 * тип своим строковым идентификатором — он сверяется с секцией
 * {@code explosions} у типа привата в regions.yml.
 *
 * <p>Событие вызывается только когда взрыв вообще задел чей-то приват
 * и осада включена ({@code siege.enabled}) — в чистом поле оно не летает.</p>
 *
 * <p>Пример аддона «Динамит A» (радиус ×3, как на HolyWorld):</p>
 * <pre>{@code
 * @EventHandler
 * public void onClassify(RegionExplosionTypeEvent event) {
 *     if (event.getEntity() instanceof TNTPrimed tnt
 *             && tnt.getPersistentDataContainer().has(dynamiteKey, PersistentDataType.STRING)) {
 *         event.setExplosionType("DYNAMITE_A");
 *         event.setDamageRadiusMultiplier(3.0);
 *     }
 * }
 * }</pre>
 */
public class RegionExplosionTypeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Entity entity;
    private final Block block;
    private final String defaultType;

    private String explosionType;
    private double damageRadiusMultiplier = 1.0;

    public RegionExplosionTypeEvent(@Nullable Entity entity, @Nullable Block block, String defaultType) {
        this.entity = entity;
        this.block = block;
        this.defaultType = defaultType;
        this.explosionType = defaultType;
    }

    /** Сущность-источник взрыва или null (взрыв блока, например кровать в Незере). */
    @Nullable
    public Entity getEntity() { return entity; }

    /** Блок-источник взрыва или null (взрыв сущности: ТНТ, крипер, визер...). */
    @Nullable
    public Block getBlock() { return block; }

    /** Тип, который QPS определил сам: TNT, CREEPER, WITHER, ENDER_CRYSTAL, BED, WIND_CHARGE. */
    public String getDefaultType() { return defaultType; }

    /** Тип взрыва для правил {@code explosions}; по умолчанию — {@link #getDefaultType()}. */
    public String getExplosionType() { return explosionType; }

    /** Подменить тип (null откатывает к {@link #getDefaultType()}). Регистр не важен. */
    public void setExplosionType(String explosionType) {
        this.explosionType = explosionType == null ? defaultType : explosionType;
    }

    /**
     * Множитель радиуса, в пределах которого взрыв снимает прочность ядра
     * ({@code siege.explosion_damage_radius} или переопределение типа).
     * Динамит A на HolyWorld — 3.0, динамит B — 10.0, по умолчанию 1.0.
     */
    public double getDamageRadiusMultiplier() { return damageRadiusMultiplier; }

    public void setDamageRadiusMultiplier(double multiplier) {
        this.damageRadiusMultiplier = Math.max(0.01, multiplier);
    }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
