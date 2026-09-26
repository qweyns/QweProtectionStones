package org.qweyns.qweprotectstones.storage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Последняя операция на ключ. Подтверждаем только успешную запись именно этой версии. */
final class RetryQueue<K> {
    // Именно идентичность версии, даже если вызывающий повторно использует тот же Runnable.
    private static final class Pending {
        private final Runnable write;
        private Pending(Runnable write) { this.write = write; }
    }
    private final Map<K, Pending> pending = new ConcurrentHashMap<>();

    void put(K key, Runnable write) { pending.put(key, new Pending(write)); }
    void putIf(K key, java.util.function.BooleanSupplier condition, Runnable write) {
        pending.compute(key, (k, old) -> condition.getAsBoolean() ? new Pending(write) : old);
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
