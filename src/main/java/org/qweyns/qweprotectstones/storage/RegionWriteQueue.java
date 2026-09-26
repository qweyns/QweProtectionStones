package org.qweyns.qweprotectstones.storage;

import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.storage.dao.RegionDao;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Версионная очередь регионов с пакетными SQL-транзакциями, а не транзакцией на каждый приват. */
final class RegionWriteQueue {
    private static final class Write {
        private final Region region; // null — удаление
        private Write(Region region) { this.region = region; }
    }
    private final Map<UUID, Write> pending = new ConcurrentHashMap<>();
    void save(Region region, Predicate<Region> live) {
        pending.compute(region.getId(), (id, old) -> live.test(region) ? new Write(region) : old);
    }
    void delete(UUID id) { pending.put(id, new Write(null)); }
    int size() { return pending.size(); }

    List<Map<String,Object>> recovery(Predicate<Region> live) {
        List<Map<String,Object>> result = new ArrayList<>();
        pending.forEach((id, write) -> {
            if (write.region == null) result.add(StorageRecovery.command("deleteRegion",id));
            else if (live.test(write.region)) result.add(StorageRecovery.region(write.region));
        });
        return result;
    }

    void flush(RegionDao dao, Predicate<Region> live, Consumer<RuntimeException> failure) {
        Map<UUID, Write> saves = new LinkedHashMap<>(), deletes = new LinkedHashMap<>();
        List<Region> snapshots = new ArrayList<>();
        for (var entry : List.copyOf(pending.entrySet())) {
            Write write = entry.getValue();
            if (write.region == null) deletes.put(entry.getKey(), write);
            else if (live.test(write.region)) {
                saves.put(entry.getKey(), write);
                snapshots.add(write.region.snapshot());
            } else pending.remove(entry.getKey(), write);
        }
        if (!saves.isEmpty()) {
            try {
                dao.saveAll(snapshots);
                saves.forEach((id, write) -> pending.remove(id, write));
            } catch (RuntimeException e) { failure.accept(e); }
        }
        if (!deletes.isEmpty()) {
            try {
                dao.deleteAll(List.copyOf(deletes.keySet()));
                deletes.forEach((id, write) -> pending.remove(id, write));
            } catch (RuntimeException e) { failure.accept(e); }
        }
    }
}
