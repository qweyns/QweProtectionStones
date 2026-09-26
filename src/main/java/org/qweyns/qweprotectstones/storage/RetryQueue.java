package org.qweyns.qweprotectstones.storage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Последняя операция на ключ. Подтверждаем только успешную запись именно этой версии. */
final class RetryQueue<K> {
    // Именно идентичность версии, даже если вызывающий повторно использует тот же Runnable.
    private static final class Pending {
        private final Runnable write;
        private final java.util.Map<String,Object> recovery;
        private Pending(Runnable write, java.util.Map<String,Object> recovery) { this.write = write; this.recovery = recovery; }
    }
    private final Map<K, Pending> pending = new ConcurrentHashMap<>();

    void put(K key, Runnable write) { put(key, write, java.util.Map.of()); }
    void put(K key, Runnable write, java.util.Map<String,Object> recovery) { pending.put(key, new Pending(write, recovery)); }
    java.util.List<java.util.Map<String,Object>> recovery() {
        return pending.values().stream().map(p -> p.recovery).filter(data -> !data.isEmpty()).toList();
    }
    int size() { return pending.size(); }
    void flush(Consumer<RuntimeException> failure) {
        for (var entry : java.util.List.copyOf(pending.entrySet())) {
            try {
                entry.getValue().write.run();
                pending.remove(entry.getKey(), entry.getValue());
            } catch (RuntimeException e) {
                // Новая операция (в том числе delete) не заменяется старой при повторе.
                failure.accept(e);
            }
        }
    }
}
