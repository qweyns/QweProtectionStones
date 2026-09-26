package org.qweyns.qweprotectstones.storage;

import org.qweyns.qweprotectstones.storage.dao.RegionDao;
import org.qweyns.qweprotectstones.storage.dao.RegionLogEntry;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Журнал пишется одной SQL-транзакцией на пакет, а не на каждый сломанный блок. */
final class LogWriteQueue {
    private final Map<UUID, RegionLogEntry> entries = new ConcurrentHashMap<>();
    void add(RegionLogEntry entry) { entries.put(UUID.randomUUID(),entry); }
    int size() { return entries.size(); }
    void flush(RegionDao dao, Consumer<RuntimeException> failure) {
        Map<UUID, RegionLogEntry> batch = Map.copyOf(entries);
        if (batch.isEmpty()) return;
        try {
            dao.appendLog(List.copyOf(batch.values()));
            batch.forEach((id, entry) -> entries.remove(id,entry));
        } catch (RuntimeException e) { failure.accept(e); }
    }
    List<Map<String,Object>> recovery() {
        List<Map<String,Object>> result = new ArrayList<>();
        entries.forEach((id,entry) -> result.add(StorageRecovery.command("log",id,"region",entry.regionId().toString(),
                "at",entry.at(),"name",entry.playerName(),"action",entry.action(),"detail",entry.detail())));
        return result;
    }
}
