package org.qweyns.pshologramm.holograms;

import org.bukkit.Location;

public interface IHologramProvider {
    void createOrUpdate(String regionId, Location loc, String regionType, String owner, int dur, int maxDur);
    void remove(String regionId);
    void deleteAll();
}
