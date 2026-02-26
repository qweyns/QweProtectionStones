package org.qweyns.pshologramm.storage;

import org.qweyns.pshologramm.models.RegionData;
import java.util.Map;

public interface StorageProvider {
    void init();
    Map<String, RegionData> loadAll();
    void save(RegionData data);
    void remove(String id);
    void close();
}
