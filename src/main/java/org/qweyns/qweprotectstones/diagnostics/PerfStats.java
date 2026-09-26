package org.qweyns.qweprotectstones.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Лёгкий счётчик времени обработчиков для /qps perf. Потокобезопасен (Folia),
 * без блокировок: на горячем пути только два LongAdder и сравнение максимума.
 */
public final class PerfStats {

    public static final class Timer {
        private final LongAdder calls = new LongAdder();
        private final LongAdder nanos = new LongAdder();
        private volatile long maxNanos;

        public void record(long elapsed) {
            calls.increment();
            nanos.add(elapsed);
            if (elapsed > maxNanos) maxNanos = elapsed; // гонка допустима: это диагностика
        }

        public long calls() { return calls.sum(); }
        public long totalNanos() { return nanos.sum(); }
        public long maxNanos() { return maxNanos; }

        void reset() { calls.reset(); nanos.reset(); maxNanos = 0; }
    }

    public record Snapshot(String name, long calls, double avgMicros, double maxMillis, double totalMillis) { }

    private final Map<String, Timer> timers = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;
    private volatile long since = System.currentTimeMillis();

    public boolean enabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Timer timer(String name) {
        return timers.computeIfAbsent(name, k -> new Timer());
    }

    /** Начало замера; 0 — замеры выключены. */
    public long start() {
        return enabled ? System.nanoTime() : 0L;
    }

    public void stop(Timer timer, long started) {
        if (started != 0L) timer.record(System.nanoTime() - started);
    }

    public long sinceMillis() { return since; }

    public void reset() {
        timers.values().forEach(Timer::reset);
        since = System.currentTimeMillis();
    }

    /** Замеры по убыванию суммарного времени. */
    public List<Snapshot> snapshot() {
        List<Snapshot> result = new ArrayList<>();
        timers.forEach((name, t) -> {
            long calls = t.calls();
            if (calls == 0) return;
            long total = t.totalNanos();
            result.add(new Snapshot(name, calls, total / 1000.0 / calls, t.maxNanos() / 1_000_000.0, total / 1_000_000.0));
        });
        result.sort((a, b) -> Double.compare(b.totalMillis(), a.totalMillis()));
        return result;
    }
}
