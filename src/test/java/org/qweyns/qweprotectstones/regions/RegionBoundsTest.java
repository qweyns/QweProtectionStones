package org.qweyns.qweprotectstones.regions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionBoundsTest {

    @Test
    void constructorSwapsInvertedBounds() {
        RegionBounds bounds = new RegionBounds(10, 10, 10, 0, 0, 0);

        assertEquals(0, bounds.minX());
        assertEquals(0, bounds.minY());
        assertEquals(0, bounds.minZ());
        assertEquals(10, bounds.maxX());
        assertEquals(10, bounds.maxY());
        assertEquals(10, bounds.maxZ());
    }

    @Test
    void aroundClampsToWorldHeight() {

        RegionBounds bounds = RegionBounds.around(0, 64, 0, 16, 512, 16, -64, 319);

        assertEquals(-64, bounds.minY());
        assertEquals(319, bounds.maxY());
        assertEquals(-16, bounds.minX());
        assertEquals(16, bounds.maxX());
        assertEquals(-16, bounds.minZ());
        assertEquals(16, bounds.maxZ());
    }

    @Test
    void containsIsInclusive() {
        RegionBounds bounds = new RegionBounds(0, 0, 0, 10, 10, 10);

        assertTrue(bounds.contains(0, 0, 0), "минимальный угол принадлежит привату");
        assertTrue(bounds.contains(10, 10, 10), "максимальный угол принадлежит привату");
        assertTrue(bounds.contains(5, 5, 5));

        assertFalse(bounds.contains(-1, 5, 5));
        assertFalse(bounds.contains(11, 5, 5));
        assertFalse(bounds.contains(5, -1, 5));
        assertFalse(bounds.contains(5, 11, 5));
        assertFalse(bounds.contains(5, 5, -1));
        assertFalse(bounds.contains(5, 5, 11));
    }

    @Test
    void containsColumnIgnoresHeight() {
        RegionBounds bounds = new RegionBounds(0, 0, 0, 10, 10, 10);

        assertTrue(bounds.containsColumn(0, 0));
        assertTrue(bounds.containsColumn(10, 10));
        assertFalse(bounds.containsColumn(11, 5));
        assertFalse(bounds.containsColumn(5, 11));
    }

    @Test
    void touchingBoundsIntersect() {

        RegionBounds first = new RegionBounds(0, 0, 0, 5, 5, 5);
        RegionBounds touching = new RegionBounds(5, 5, 5, 10, 10, 10);
        RegionBounds adjacent = new RegionBounds(6, 0, 0, 10, 10, 10);
        RegionBounds far = new RegionBounds(100, 100, 100, 110, 110, 110);

        assertTrue(first.intersects(touching), "общий блок — уже пересечение");
        assertFalse(first.intersects(adjacent), "соседний блок без общего блока — не пересечение");
        assertFalse(first.intersects(far));
    }

    @Test
    void expandGrowsInAllDirections() {
        RegionBounds bounds = new RegionBounds(0, 0, 0, 10, 10, 10);
        RegionBounds expanded = bounds.expand(2);

        assertEquals(new RegionBounds(-2, -2, -2, 12, 12, 12), expanded);
    }


    @Test
    void sizesCountBlocksNotCoordinates() {
        RegionBounds bounds = new RegionBounds(-8, -64, -8, 7, 319, 7);

        assertEquals(16, bounds.sizeX());
        assertEquals(384, bounds.sizeY());
        assertEquals(16, bounds.sizeZ());
        assertEquals(256L, bounds.area());
        assertEquals(16L * 384L * 16L, bounds.volume());
    }

    @Test
    void centerOfEvenSideFallsOnLowerHalf() {
        RegionBounds bounds = new RegionBounds(0, 0, 0, 15, 10, 15);

        assertEquals(8, bounds.centerX());
        assertEquals(8, bounds.centerZ());
    }

    @Test
    void chunkCoordinatesUseArithmeticShift() {

        RegionBounds bounds = new RegionBounds(-17, 0, -17, 32, 10, 32);

        assertEquals(-2, bounds.chunkMinX());
        assertEquals(2, bounds.chunkMaxX());
        assertEquals(-2, bounds.chunkMinZ());
        assertEquals(2, bounds.chunkMaxZ());
    }
}
