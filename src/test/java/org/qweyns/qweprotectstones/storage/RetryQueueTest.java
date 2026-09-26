package org.qweyns.qweprotectstones.storage;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RetryQueueTest {
    @Test void failedWriteRemainsUntilAcknowledged() {
        RetryQueue<String> queue = new RetryQueue<>();
        AtomicInteger calls = new AtomicInteger();
        queue.put("id", () -> { if (calls.incrementAndGet() == 1) throw new IllegalStateException(); });
        queue.flush(e -> { });
        assertEquals(1, queue.size());
        queue.flush(e -> fail(e));
        assertEquals(0, queue.size());
        assertEquals(2, calls.get());
    }
    @Test void deletionArrivingDuringSaveIsNotAcknowledgedBySave() {
        RetryQueue<String> queue = new RetryQueue<>();
        AtomicInteger deletes = new AtomicInteger();
        queue.put("id", () -> queue.put("id", deletes::incrementAndGet));
        queue.flush(e -> fail(e));
        assertEquals(1, queue.size());
        queue.flush(e -> fail(e));
        assertEquals(1, deletes.get());
        assertEquals(0, queue.size());
    }
    @Test void failureNeverOverwritesNewerOperation() {
        RetryQueue<String> queue = new RetryQueue<>();
        AtomicInteger deletes = new AtomicInteger();
        queue.put("id", () -> { queue.put("id", deletes::incrementAndGet); throw new IllegalStateException(); });
        queue.flush(e -> { });
        queue.flush(e -> fail(e));
        assertEquals(1, deletes.get());
    }
}
