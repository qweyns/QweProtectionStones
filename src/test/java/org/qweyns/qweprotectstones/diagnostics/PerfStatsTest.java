package org.qweyns.qweprotectstones.diagnostics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerfStatsTest {

    @Test
    void recordsCallsAndSortsByTotalTime() {
        PerfStats stats = new PerfStats();
        stats.timer("fast").record(1_000);
        stats.timer("slow").record(5_000_000);
        stats.timer("slow").record(1_000_000);

        var snapshot = stats.snapshot();
        assertEquals("slow", snapshot.get(0).name());
        assertEquals(2, snapshot.get(0).calls());
        assertEquals(5.0, snapshot.get(0).maxMillis(), 1e-9);
        assertEquals(3000.0, snapshot.get(0).avgMicros(), 1e-9);
        assertEquals("fast", snapshot.get(1).name());
    }

    @Test
    void disabledStatsDoNotRecord() {
        PerfStats stats = new PerfStats();
        stats.setEnabled(false);
        long started = stats.start();
        assertEquals(0L, started);
        stats.stop(stats.timer("x"), started);
        assertTrue(stats.snapshot().isEmpty());
    }

    @Test
    void resetClearsCounters() {
        PerfStats stats = new PerfStats();
        stats.timer("x").record(10);
        stats.reset();
        assertTrue(stats.snapshot().isEmpty());
    }
}
