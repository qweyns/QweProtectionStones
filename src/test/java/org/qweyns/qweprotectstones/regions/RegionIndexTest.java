package org.qweyns.qweprotectstones.regions;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionIndexTest {

    private record Stub(String world, RegionBounds bounds) implements Bounded {
        @Override
        public String getWorldName() {
            return world;
        }

        @Override
        public RegionBounds getBounds() {
            return bounds;
        }
    }

    private final RegionIndex<Stub> index = new RegionIndex<>();

    @Test
    void pointInsideRegionIsFound() {
        Stub region = new Stub("world", new RegionBounds(0, 0, 0, 10, 10, 10));
        index.add(region);

        assertSame(region, index.at("world", 5, 5, 5));
        assertSame(region, index.at("world", 10, 10, 10), "границы включительные");
        assertNull(index.at("world", 11, 5, 5));
    }

    @Test
    void worldsAreIsolated() {
        Stub overworld = new Stub("world", new RegionBounds(0, 0, 0, 10, 10, 10));
        Stub nether = new Stub("nether", new RegionBounds(0, 0, 0, 10, 10, 10));
        index.add(overworld);
        index.add(nether);

        assertSame(nether, index.at("nether", 5, 5, 5));
        assertSame(overworld, index.at("world", 5, 5, 5));
    }

    @Test
    void regionSpanningChunksIsFoundFromEveryChunk() {
        // 33x33 блока накрывает 3x3 чанка.
        Stub region = new Stub("world", new RegionBounds(0, 0, 0, 32, 10, 32));
        index.add(region);

        assertSame(region, index.at("world", 1, 5, 1));
        assertSame(region, index.at("world", 16, 5, 16));
        assertSame(region, index.at("world", 31, 5, 31));
        assertNull(index.at("world", 48, 5, 48));
    }

    @Test
    void negativeCoordinatesWork() {
        Stub region = new Stub("world", new RegionBounds(-100, 0, -100, -60, 10, -60));
        index.add(region);

        assertSame(region, index.at("world", -80, 5, -80));
        assertNull(index.at("world", -59, 5, -80));
    }

    @Test
    void removedRegionIsNotFound() {
        Stub region = new Stub("world", new RegionBounds(0, 0, 0, 10, 10, 10));
        index.add(region);
        index.remove(region);

        assertNull(index.at("world", 5, 5, 5));
        assertTrue(index.intersecting("world", region.getBounds()).isEmpty());
    }

    @Test
    void intersectingReturnsAllOverlapsWithoutDuplicates() {
        Stub first = new Stub("world", new RegionBounds(0, 0, 0, 20, 10, 20));
        Stub second = new Stub("world", new RegionBounds(5, 0, 5, 25, 10, 25));
        Stub far = new Stub("world", new RegionBounds(100, 0, 100, 110, 10, 110));
        index.add(first);
        index.add(second);
        index.add(far);

        List<Stub> found = index.intersecting("world", new RegionBounds(0, 0, 0, 30, 10, 30));

        assertEquals(2, found.size());
        assertTrue(found.contains(first));
        assertTrue(found.contains(second));

        // порядок не гарантирован, важен сам факт находки
        Stub hit = index.firstIntersecting("world", new RegionBounds(10, 5, 10, 11, 5, 11));
        assertTrue(hit == first || hit == second);
        assertNull(index.firstIntersecting("world", new RegionBounds(200, 5, 200, 210, 5, 210)));
    }

    @Test
    void inChunkReturnsRegisteredValues() {
        Stub region = new Stub("world", new RegionBounds(0, 0, 0, 20, 10, 20));
        index.add(region);

        assertEquals(Set.of(region), index.inChunk("world", 0, 0));
        assertEquals(Set.of(region), index.inChunk("world", 1, 1));
        assertTrue(index.inChunk("world", 5, 5).isEmpty());
    }

    @Test
    void removeCleansUpEmptyWorlds() {
        Stub region = new Stub("lonely", new RegionBounds(0, 0, 0, 10, 10, 10));
        index.add(region);
        index.remove(region);

        assertTrue(index.inChunk("lonely", 0, 0).isEmpty());
    }

    @Test
    void chunkKeySeparatesNegativeAndPositiveChunks() {
        // -1 в чанк -1, ключи не должны склеиваться из-за сдвига

        assertTrue(RegionIndex.chunkKey(-1, -1) != RegionIndex.chunkKey(0, 0));
        assertEquals(RegionIndex.chunkKey(-1, -1), RegionIndex.chunkKey(-1, -1));

        Stub negative = new Stub("world", new RegionBounds(-16, 0, -16, -1, 10, -1));
        index.add(negative);

        assertNull(index.at("world", 0, 5, 0), "блок 0 — уже другой чанк и другая область");
        assertSame(negative, index.at("world", -1, 5, -1));
    }
}
