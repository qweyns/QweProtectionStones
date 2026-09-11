package org.qweyns.qweprotectstones.regions;

/**
 * Способ получения привата.
 *
 * <ul>
 *   <li>{@link #SURVIVAL} — обычный блок: добывается, крафтится и ставится
 *       как любой блок Minecraft (по умолчанию);</li>
 *   <li>{@link #COMMAND} — «покупной»: приват создаётся только предметом,
 *       выданным командой {@code /qps give} (предмет помечен NBT/PDC-тегом
 *       типа и имеет собственное описание). Обычный блок того же материала
 *       приват не создаёт, а крафт для таких типов недоступен.</li>
 * </ul>
 */
public enum RegionSource {
    SURVIVAL,
    COMMAND;

    /** Читает значение из конфига: неизвестное слово откатывается к SURVIVAL. */
    public static RegionSource parse(String raw) {
        if (raw == null) return SURVIVAL;
        return switch (raw.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "COMMAND", "PURCHASE", "ADMIN" -> COMMAND;
            default -> SURVIVAL;
        };
    }
}
