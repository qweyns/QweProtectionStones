package org.qweyns.pshologramm.storage.dao;

import org.qweyns.pshologramm.models.RegionData;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

public interface RegionDao {
    void init();
    Map<String, RegionData> loadAll();
    void saveAll(Collection<RegionData> regions);
    void deleteAll(Collection<String> ids);

    void loadAutoAdd(UUID uuid, BiConsumer<Set<String>, Boolean> callback);
    void saveAutoAdd(UUID uuid, Set<String> friends, boolean toggledOff);

    void close();
}
