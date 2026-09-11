package org.qweyns.qweprotectstones.regions;

public record RegionBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public RegionBounds {
        if (minX > maxX) { int t = minX; minX = maxX; maxX = t; }
        if (minY > maxY) { int t = minY; minY = maxY; maxY = t; }
        if (minZ > maxZ) { int t = minZ; minZ = maxZ; maxZ = t; }
    }

    public static RegionBounds around(int x, int y, int z,
                                     int radiusX, int radiusY, int radiusZ,
                                     int worldMinY, int worldMaxY) {
        return new RegionBounds(
                x - radiusX, Math.max(worldMinY, y - radiusY), z - radiusZ,
                x + radiusX, Math.min(worldMaxY, y + radiusY), z + radiusZ);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean containsColumn(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean intersects(RegionBounds other) {
        return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    public RegionBounds expand(int amount) {
        return new RegionBounds(minX - amount, minY - amount, minZ - amount,
                maxX + amount, maxY + amount, maxZ + amount);
    }

    public RegionBounds expandHorizontally(int amount) {
        return new RegionBounds(minX - amount, minY, minZ - amount,
                maxX + amount, maxY, maxZ + amount);
    }

    public int chunkMinX() { return minX >> 4; }
    public int chunkMaxX() { return maxX >> 4; }
    public int chunkMinZ() { return minZ >> 4; }
    public int chunkMaxZ() { return maxZ >> 4; }

    public int sizeX() { return maxX - minX + 1; }
    public int sizeY() { return maxY - minY + 1; }
    public int sizeZ() { return maxZ - minZ + 1; }

    public long volume() { return (long) sizeX() * sizeY() * sizeZ(); }

    public long area() { return (long) sizeX() * sizeZ(); }

    public int centerX() { return minX + sizeX() / 2; }
    public int centerZ() { return minZ + sizeZ() / 2; }
}
