package org.qweyns.qweprotectstones.regions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Пространственный индекс: мир -> чанк -> объекты, задевающие этот чанк.
 *
 * <p>Поиск по координате выполняется на каждое движение игрока и на каждое
 * событие защиты, поэтому он обязан быть O(1) по числу приватов на сервере.
 * Приват регистрируется во всех чанках, которые пересекает — при радиусе 16
 * это 3x3 чанка, то есть память расходуется незначительно.</p>
 *
 * <p>Индекс работает с {@link Bounded}, а не с {@code Region}, поэтому не
 * зависит от Bukkit и покрыт юнит-тестами.</p>
 *
 * @param <T> тип индексируемого объекта
 */
public final class RegionIndex<T extends Bounded> {

    private final Map<String, Map<Long, Set<T>>> byWorld = new ConcurrentHashMap<>();

    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public void add(T value) {
        Map<Long, Set<T>> world = byWorld.computeIfAbsent(value.getWorldName(), k -> new ConcurrentHashMap<>());
        RegionBounds bounds = value.getBounds();

        for (int cx = bounds.chunkMinX(); cx <= bounds.chunkMaxX(); cx++) {
            for (int cz = bounds.chunkMinZ(); cz <= bounds.chunkMaxZ(); cz++) {
                world.computeIfAbsent(chunkKey(cx, cz), k -> ConcurrentHashMap.newKeySet()).add(value);
            }
        }
    }

    public void remove(T value) {
        Map<Long, Set<T>> world = byWorld.get(value.getWorldName());
        if (world == null) return;

        RegionBounds bounds = value.getBounds();
        for (int cx = bounds.chunkMinX(); cx <= bounds.chunkMaxX(); cx++) {
            for (int cz = bounds.chunkMinZ(); cz <= bounds.chunkMaxZ(); cz++) {
                long key = chunkKey(cx, cz);
                Set<T> values = world.get(key);
                if (values == null) continue;

                values.remove(value);
                if (values.isEmpty()) world.remove(key, values);
            }
        }
        if (world.isEmpty()) byWorld.remove(value.getWorldName(), world);
    }

    /** Объект, накрывающий точку. Пересечения запрещены, поэтому он не более одного. */
    public T at(String worldName, int x, int y, int z) {
        for (T value : inChunk(worldName, x >> 4, z >> 4)) {
            if (value.getBounds().contains(x, y, z)) return value;
        }
        return null;
    }

    public Set<T> inChunk(String worldName, int chunkX, int chunkZ) {
        Map<Long, Set<T>> world = byWorld.get(worldName);
        if (world == null) return Set.of();

        Set<T> values = world.get(chunkKey(chunkX, chunkZ));
        return values == null ? Set.of() : values;
    }

    /** Всё, что пересекается с областью — для проверки наложения при создании. */
    public List<T> intersecting(String worldName, RegionBounds bounds) {
        Map<Long, Set<T>> world = byWorld.get(worldName);
        if (world == null) return List.of();

        // LinkedHashSet вместо списка с contains: при большой области проверка
        // на дубликаты линейным поиском становилась квадратичной.
        Set<T> result = new LinkedHashSet<>();
        for (int cx = bounds.chunkMinX(); cx <= bounds.chunkMaxX(); cx++) {
            for (int cz = bounds.chunkMinZ(); cz <= bounds.chunkMaxZ(); cz++) {
                for (T value : world.getOrDefault(chunkKey(cx, cz), Collections.emptySet())) {
                    if (value.getBounds().intersects(bounds)) result.add(value);
                }
            }
        }
        return new ArrayList<>(result);
    }

    /** Первый найденный объект, пересекающийся с областью, или null. */
    public T firstIntersecting(String worldName, RegionBounds bounds) {
        Map<Long, Set<T>> world = byWorld.get(worldName);
        if (world == null) return null;

        for (int cx = bounds.chunkMinX(); cx <= bounds.chunkMaxX(); cx++) {
            for (int cz = bounds.chunkMinZ(); cz <= bounds.chunkMaxZ(); cz++) {
                for (T value : world.getOrDefault(chunkKey(cx, cz), Collections.emptySet())) {
                    if (value.getBounds().intersects(bounds)) return value;
                }
            }
        }
        return null;
    }

    public void clear() {
        byWorld.clear();
    }
}
