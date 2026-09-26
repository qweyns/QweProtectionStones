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
    // версия привата, которая точно лежит в базе: по ней при остановке дописываются
    // только реально изменённые приваты, а не вся база
    private final Map<UUID, Long> persisted = new ConcurrentHashMap<>();

    void markPersisted(Region region) { persisted.put(region.getId(), region.getVersion()); }

    boolean isDirty(Region region) {
        Long version = persisted.get(region.getId());
        return version == null || version != region.getVersion();
    }
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
        Map<UUID, Long> versions = new LinkedHashMap<>();
        for (var entry : List.copyOf(pending.entrySet())) {
            Write write = entry.getValue();
            if (write.region == null) deletes.put(entry.getKey(), write);
            else if (live.test(write.region)) {
                saves.put(entry.getKey(), write);
                // версию читаем до снимка: изменение после снимка останется «грязным»
                versions.put(entry.getKey(), write.region.getVersion());
                snapshots.add(write.region.snapshot());
            } else pending.remove(entry.getKey(), write);
        }
        if (!saves.isEmpty()) {
            try {
                dao.saveAll(snapshots);
                saves.forEach((id, write) -> pending.remove(id, write));
                persisted.putAll(versions);
            } catch (RuntimeException e) { failure.accept(e); }
        }
        if (!deletes.isEmpty()) {
            try {
                dao.deleteAll(List.copyOf(deletes.keySet()));
                deletes.forEach((id, write) -> pending.remove(id, write));
                deletes.keySet().forEach(persisted::remove);
            } catch (RuntimeException e) { failure.accept(e); }
        }
    }
}
