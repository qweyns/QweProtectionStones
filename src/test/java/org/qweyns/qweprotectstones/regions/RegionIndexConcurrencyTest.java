package org.qweyns.qweprotectstones.regions;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RegionIndexConcurrencyTest {
    @Test void addCannotBeLostWhenLastOldEntryIsRemoved() throws Exception {
        RegionIndex<Region> index = new RegionIndex<>();
        Region old = RegionMutationTest.region();
        Region added = RegionMutationTest.region();
        try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 500; iteration++) {
                index.clear(); index.add(old);
                CountDownLatch go = new CountDownLatch(1);
                Future<?> remove = threads.submit(() -> { await(go); index.remove(old); });
                Future<?> add = threads.submit(() -> { await(go); index.add(added); });
                go.countDown();
                remove.get(5, TimeUnit.SECONDS); add.get(5, TimeUnit.SECONDS);
                assertSame(added, index.at("world",0,5,0));
            }
        }
    }
    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
}
