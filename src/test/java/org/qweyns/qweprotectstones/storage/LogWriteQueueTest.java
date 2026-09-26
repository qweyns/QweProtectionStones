package org.qweyns.qweprotectstones.storage;

import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.storage.dao.RegionDao;
import org.qweyns.qweprotectstones.storage.dao.RegionLogEntry;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogWriteQueueTest {
    @Test void logBatchRetriesAndDoesNotAcknowledgeNewEntries() {
        LogWriteQueue queue = new LogWriteQueue();
        RegionDao dao = mock(RegionDao.class);
        var entry = new RegionLogEntry(UUID.randomUUID(),123,"player","break","STONE");
        queue.add(entry); queue.add(entry);
        doThrow(new IllegalStateException()).when(dao).appendLog(anyCollection());
        queue.flush(dao,e -> { });
        assertEquals(2,queue.size());
        reset(dao);
        doAnswer(call -> { queue.add(entry); return null; }).when(dao).appendLog(anyCollection());
        queue.flush(dao,e -> fail(e));
        verify(dao).appendLog(argThat(batch -> batch.size() == 2));
        assertEquals(1,queue.size());
        reset(dao);
        queue.flush(dao,e -> fail(e));
        assertEquals(0,queue.size());
    }
}
