package org.qweyns.qweprotectstones.storage.dao;

import org.qweyns.qweprotectstones.features.market.RegionRental;
import org.qweyns.qweprotectstones.features.market.RegionSale;
import org.qweyns.qweprotectstones.regions.Region;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

public interface RegionDao {

    void init();

    List<Region> loadAll();

    void saveAll(Collection<Region> regions);

    void deleteAll(Collection<UUID> regionIds);

    void loadAutoAdd(UUID uuid, BiConsumer<Set<String>, Boolean> callback);

    void saveAutoAdd(UUID uuid, Set<String> friends, boolean toggledOff);

    void touchPlayer(UUID uuid, String name, long lastSeen);

    Map<UUID, Long> loadLastSeen();

    void appendLog(Collection<RegionLogEntry> entries);

    List<RegionLogEntry> readLog(UUID regionId, int limit);

    int pruneLog(long olderThan);

    Map<UUID, RegionSale> loadSales();

    void saveSale(RegionSale sale);

    void deleteSale(UUID regionId);

    Map<UUID, RegionRental> loadRentals();

    void saveRental(RegionRental rental);

    void deleteRental(UUID regionId);

    void close();
}
