package org.qweyns.qweprotectstones.regions;

import java.util.Locale;
import java.util.Optional;

/**
 * Уровни доступа внутри привата. В ProtectionStones было плоско — владелец и
 * участник; здесь между ними есть градация, поэтому друга можно пустить
 * к сундукам, не давая ему ломать постройки.
 *
 * <p>Уровни строго вложены: каждый следующий включает права предыдущего.</p>
 */
public enum TrustLevel {
    /** Двери, люки, кнопки, рычаги, кровати. */
    ACCESS(1),
    /** + сундуки, бочки, печи, воронки и прочие контейнеры. */
    CONTAINER(2),
    /** + установка и разрушение блоков, работа с сущностями. */
    BUILD(3),
    /** + приглашение игроков, изменение флагов, покупка улучшений. */
    MANAGER(4),
    /** Полный контроль, включая удаление привата и передачу прав. */
    OWNER(5);

    private final int weight;

    TrustLevel(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    /** Достаточно ли этого уровня для действия, требующего {@code required}. */
    public boolean atLeast(TrustLevel required) {
        return weight >= required.weight;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<TrustLevel> parse(String raw) {
        if (raw == null) return Optional.empty();
        for (TrustLevel level : values()) {
            if (level.name().equalsIgnoreCase(raw.trim())) return Optional.of(level);
        }
        return Optional.empty();
    }

    /** Уровни, которые можно выдать другому игроку (OWNER выдаётся только передачей привата). */
    public static TrustLevel[] grantable() {
        return new TrustLevel[]{ACCESS, CONTAINER, BUILD, MANAGER};
    }
}
