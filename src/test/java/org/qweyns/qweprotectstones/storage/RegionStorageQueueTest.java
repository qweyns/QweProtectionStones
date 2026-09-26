package org.qweyns.qweprotectstones.storage;

import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionManager;
import org.qweyns.qweprotectstones.scheduler.Schedulers;
import org.qweyns.qweprotectstones.storage.dao.RegionDao;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.*;

class RegionStorageQueueTest {
    @Test
    void delayedSaveNowCannotResurrectDeletedRegion() throws Exception {
        QweProtectStones plugin = mock(QweProtectStones.class);
        Schedulers schedulers = mock(Schedulers.class);
        RegionManager manager = mock(RegionManager.class);
        when(plugin.getSchedulers()).thenReturn(schedulers);
        when(plugin.getRegionManager()).thenReturn(manager);
        Region region = mock(Region.class);
        UUID id = UUID.randomUUID();
        when(region.getId()).thenReturn(id);
        when(manager.getById(id)).thenReturn(region);
        RegionStorage storage = new RegionStorage(plugin);
        RegionDao dao = mock(RegionDao.class);
        var field = RegionStorage.class.getDeclaredField("dao");
        field.setAccessible(true);
        field.set(storage, dao);
        AtomicReference<Runnable> delayed = new AtomicReference<>();
        doAnswer(call -> { delayed.set(call.getArgument(0)); return null; })
                .when(schedulers).runAsync(any(Runnable.class));
        storage.saveNow(region);
        when(manager.getById(id)).thenReturn(null);
        storage.delete(id);
        storage.saveSnapshot(List.of());
        delayed.get().run();
        verify(dao).deleteAll(List.of(id));
        verify(dao, never()).saveAll(anyCollection());
        storage.close();
        // Поздний callback после закрытия не трогает закрытый DAO.
        delayed.get().run();
        verify(dao).close();
    }
}
