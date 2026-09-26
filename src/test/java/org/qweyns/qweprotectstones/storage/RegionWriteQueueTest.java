package org.qweyns.qweprotectstones.storage;

import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;
import org.qweyns.qweprotectstones.storage.dao.RegionDao;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegionWriteQueueTest {
    private Region region() { return new Region(UUID.randomUUID(),"world",new RegionBounds(0,0,0,2,2,2),1,1,1,
            "small",UUID.randomUUID(),"owner",5,10,1); }
    @Test void failedBatchIsRetriedAndConcurrentDeleteWins() {
        Region a = region(), b = region();
        RegionWriteQueue queue = new RegionWriteQueue();
        queue.save(a, r -> true); queue.save(b, r -> true);
        RegionDao dao = mock(RegionDao.class);
        doThrow(new IllegalStateException()).when(dao).saveAll(anyCollection());
        queue.flush(dao, r -> true, e -> { });
        assertEquals(2,queue.size());
        queue.delete(a.getId());
        reset(dao);
        queue.flush(dao, r -> true, e -> fail(e));
        verify(dao).saveAll(argThat(saved -> saved.size() == 1 && saved.iterator().next().getId().equals(b.getId())));
        verify(dao).deleteAll(List.of(a.getId()));
        assertEquals(0,queue.size());
    }
    @Test void editsDuringBatchAreNotAcknowledgedByEarlierWrite() {
        Region region = region(); RegionWriteQueue queue = new RegionWriteQueue();
        queue.save(region,r -> true);
        RegionDao dao = mock(RegionDao.class);
        doAnswer(call -> { region.setDurability(2); queue.save(region,r -> true); return null; })
                .when(dao).saveAll(anyCollection());
        queue.flush(dao,r -> true,e -> fail(e));
        assertEquals(1,queue.size());
        reset(dao);
        queue.flush(dao,r -> true,e -> fail(e));
        verify(dao).saveAll(argThat(saved -> saved.iterator().next().getDurability() == 2));
        assertEquals(0,queue.size());
    }
}
