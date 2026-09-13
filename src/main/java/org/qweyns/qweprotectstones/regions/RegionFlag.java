package org.qweyns.qweprotectstones.regions;

import java.util.Locale;
import java.util.Optional;

/**
 * Флаги привата. Значение берётся в порядке: переопределение в самом привате →
 * настройка типа привата в config.yml → значение по умолчанию отсюда.
 *
 * <p>{@link #playerEditable} помечает флаги, которые владелец может менять сам
 * через меню и команды; остальные доступны только администрации.</p>
 */
public enum RegionFlag {
    /** Разрешён ли PvP внутри привата. */
    PVP(false, true),
    /** Пускать ли внутрь посторонних игроков. */
    ENTRY(true, true),
    /** Могут ли посторонние взаимодействовать без выданного доступа (публичный приват). */
    PUBLIC_ACCESS(false, true),
    /** Спавн враждебных мобов. */
    MONSTER_SPAWNING(true, true),
    /** Спавн мирных существ. */
    ANIMAL_SPAWNING(true, true),
    /** Урон по мирным существам от посторонних. */
    ANIMAL_PROTECTION(true, true),
    /** Разрушение блоков мобами: криперы, эндермены, овцы, взрывы гастов. */
    MOB_GRIEFING(false, true),
    /** Распространение и поджигание огня. */
    FIRE_SPREAD(false, true),
    /** Растекание воды и лавы снаружи внутрь привата. */
    LIQUID_FLOW_IN(false, true),
    /** Поршни снаружи, двигающие блоки привата. */
    PISTONS_FROM_OUTSIDE(false, true),
    /** Вытаптывание грядок. */
    CROP_TRAMPLE(false, true),
    /** Урон от взрывов постройкам привата (ядро считается отдельно, по прочности). */
    EXPLOSION_DAMAGE(false, true),
    /** Телепортация внутрь привата посторонними (в т.ч. жемчугом Края). */
    TELEPORT_IN(true, true),
    /** Подбирание выпавших предметов посторонними. */
    ITEM_PICKUP(true, true),
    /** Показывать сообщение при входе и выходе. */
    GREETING(true, true),

    /** Затухание листвы. Служебный флаг, игрокам обычно не нужен. */
    LEAF_DECAY(true, false),
    /** Намерзание льда и выпадение снега. */
    ICE_AND_SNOW(true, false),
    /** Рост растений и распространение травы. */
    BLOCK_GROWTH(true, false);

    private final boolean defaultValue;
    private final boolean playerEditable;

    RegionFlag(boolean defaultValue, boolean playerEditable) {
        this.defaultValue = defaultValue;
        this.playerEditable = playerEditable;
    }

    public boolean defaultValue() {
        return defaultValue;
    }

    public boolean playerEditable() {
        return playerEditable;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<RegionFlag> parse(String raw) {
        if (raw == null) return Optional.empty();
        String normalized = raw.trim().replace('-', '_');
        for (RegionFlag flag : values()) {
            if (flag.name().equalsIgnoreCase(normalized)) return Optional.of(flag);
        }
        return Optional.empty();
    }
}
