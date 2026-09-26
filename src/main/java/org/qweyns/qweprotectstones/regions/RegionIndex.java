package org.qweyns.qweprotectstones.regions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Пространственный индекс по чанкам. Чтение (поиск привата по координатам на каждом
 * блоке, шаге, взрыве) идёт без блокировок и без копирования: в каждом чанке лежит
 * неизменяемый массив, который при записи подменяется целиком (copy-on-write).
 * Запись редкая (создание, удаление, изменение границ) и сериализуется.
 */
public final class RegionIndex<T extends Bounded> {

    private static final Object[] EMPTY = new Object[0];

    private final Map<String, Map<Long, Object[]>> byWorld = new ConcurrentHashMap<>();

    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public synchronized void add(T value) {
        Map<Long, Object[]> world = byWorld.computeIfAbsent(value.getWorldName(), k -> new ConcurrentHashMap<>());
        RegionBounds bounds = value.getBounds();

        for (int cx = bounds.chunkMinX(); cx <= bounds.chunkMaxX(); cx++) {
            for (int cz = bounds.chunkMinZ(); cz <= bounds.chunkMaxZ(); cz++) {
                long key = chunkKey(cx, cz);
                Object[] current = world.getOrDefault(key, EMPTY);
                if (indexOf(current, value) >= 0) continue;
                Object[] next = Arrays.copyOf(current, current.length + 1);
                next[current.length] = value;
                world.put(key, next);
            }
        }
    }

    public synchronized void remove(T value) {
        Map<Long, Object[]> world = byWorld.get(value.getWorldName());
        if (world == null) return;

        RegionBounds bounds = value.getBounds();
        for (int cx = bounds.chunkMinX(); cx <= bounds.chunkMaxX(); cx++) {
            for (int cz = bounds.chunkMinZ(); cz <= bounds.chunkMaxZ(); cz++) {
                long key = chunkKey(cx, cz);
                Object[] current = world.get(key);
                if (current == null) continue;
                int at = indexOf(current, value);
                if (at < 0) continue;
                if (current.length == 1) {
                    world.remove(key);
                    continue;
                }
                Object[] next = new Object[current.length - 1];
                System.arraycopy(current, 0, next, 0, at);
                System.arraycopy(current, at + 1, next, at, current.length - at - 1);
                world.put(key, next);
            }
        }
        if (world.isEmpty()) byWorld.remove(value.getWorldName(), world);
    }

    private static int indexOf(Object[] values, Object value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return i;
        }
        return -1;
    }

    private Object[] chunk(String worldName, int chunkX, int chunkZ) {
        Map<Long, Object[]> world = byWorld.get(worldName);
        if (world == null) return EMPTY;
        Object[] values = world.get(chunkKey(chunkX, chunkZ));
        return values == null ? EMPTY : values;
    }

    @SuppressWarnings("unchecked")
    public T at(String worldName, int x, int y, int z) {
        if (worldName == null) return null;
        for (Object raw : chunk(worldName, x >> 4, z >> 4)) {
            T value = (T) raw;
            if (value.getBounds().contains(x, y, z)) return value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Set<T> inChunk(String worldName, int chunkX, int chunkZ) {
        Object[] values = chunk(worldName, chunkX, chunkZ);
        if (values.length == 0) return Set.of();
        Set<T> result = new LinkedHashSet<>();
        for (Object raw : values) result.add((T) raw);
        return Collections.unmodifiableSet(result);
    }

    @SuppressWarnings("unchecked")
    public List<T> intersecting(String worldName, RegionBounds bounds) {
        Map<Long, Object[]> world = byWorld.get(worldName);
        if (world == null) return List.of();

        Set<T> result = new LinkedHashSet<>();
        for (int cx = bounds.chunkMinX(); cx <= bounds.chunkMaxX(); cx++) {
            for (int cz = bounds.chunkMinZ(); cz <= bounds.chunkMaxZ(); cz++) {
                Object[] values = world.get(chunkKey(cx, cz));
                if (values == null) continue;
                for (Object raw : values) {
                    T value = (T) raw;
                    if (value.getBounds().intersects(bounds)) result.add(value);
                }
            }
        }
        return new ArrayList<>(result);
    }

    @SuppressWarnings("unchecked")
    public T firstIntersecting(String worldName, RegionBounds bounds) {
        Map<Long, Object[]> world = byWorld.get(worldName);
        if (world == null) return null;

        for (int cx = bounds.chunkMinX(); cx <= bounds.chunkMaxX(); cx++) {
            for (int cz = bounds.chunkMinZ(); cz <= bounds.chunkMaxZ(); cz++) {
                Object[] values = world.get(chunkKey(cx, cz));
                if (values == null) continue;
                for (Object raw : values) {
                    T value = (T) raw;
                    if (value.getBounds().intersects(bounds)) return value;
                }
            }
        }
        return null;
    }

    public synchronized void clear() {
        byWorld.clear();
    }
}
