package org.qweyns.pshologramm.models;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RegionData {
    private final String id;
    private String type, owner, material, world;
    private int durability, maxDurability;
    private double x, y, z;
    private final List<String> effects;

    public RegionData(String id, String type, String owner, String material, int durability, int maxDurability, String world, double x, double y, double z, List<String> effects) {
        this.id = id; this.type = type; this.owner = owner; this.material = material;
        this.durability = durability; this.maxDurability = maxDurability;
        this.world = world; this.x = x; this.y = y; this.z = z;
        this.effects = new CopyOnWriteArrayList<>(effects);
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getOwner() { return owner; }
    public String getMaterial() { return material; }
    public int getDurability() { return durability; }
    public void setDurability(int durability) { this.durability = durability; }
    public int getMaxDurability() { return maxDurability; }
    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public List<String> getEffects() { return effects; }
}
