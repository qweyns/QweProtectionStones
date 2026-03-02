package org.qweyns.pshologramm.storage.dao;

import org.qweyns.pshologramm.models.RegionData;
import java.util.Collection;
import java.util.Map;

public interface RegionDao {
    void init();
    Map<String, RegionData> loadAll();
    void saveAll(Collection<RegionData> regions);
    void deleteAll(Collection<String> ids);
    void close();
}
