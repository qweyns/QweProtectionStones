package org.qweyns.qweprotectstones.holograms;

import org.bukkit.Location;
import org.qweyns.qweprotectstones.regions.Region;

import java.util.UUID;

public interface IHologramProvider {

    void createOrUpdate(Region region, Location coreLocation);

    void remove(UUID regionId);

    void deleteAll();
}
