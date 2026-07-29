package org.qweyns.qweprotectstones.storage.dao;

import org.qweyns.qweprotectstones.regions.Region;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Доступ к хранилищу приватов. Все методы вызываются вне основного потока. */
public interface RegionDao {

    /** Создаёт или обновляет схему до актуальной версии. */
    void init();

    /** Полная загрузка при запуске: приваты вместе с участниками, флагами и банами. */
    List<Region> loadAll();

    void saveAll(Collection<Region> regions);

    void deleteAll(Collection<UUID> regionIds);

    void loadAutoAdd(UUID uuid, BiConsumer<Set<String>, Boolean> callback);

    void saveAutoAdd(UUID uuid, Set<String> friends, boolean toggledOff);

    /** Отмечает вход игрока — по этим данным находятся заброшенные приваты. */
    void touchPlayer(UUID uuid, String name, long lastSeen);

    Map<UUID, Long> loadLastSeen();

    void appendLog(Collection<RegionLogEntry> entries);

    List<RegionLogEntry> readLog(UUID regionId, int limit);

    /** @return сколько записей журнала удалено */
    int pruneLog(long olderThan);

    void close();
}
