package org.qweyns.qweprotectstones.regions;

/**
 * Минимум, который нужен пространственному индексу от объекта.
 *
 * <p>Благодаря этому интерфейсу {@link RegionIndex} не зависит от Bukkit и
 * проверяется юнит-тестами на простых заглушках.</p>
 */
public interface Bounded {

    String getWorldName();

    RegionBounds getBounds();
}
