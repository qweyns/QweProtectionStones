package org.qweyns.qweprotectstones.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Собирает предупреждения, которые плагин пишет в консоль во время загрузки конфигов,
 * чтобы показать их одним списком в /qps reload и итоговой строкой при запуске.
 * Сами проверки остаются там, где разбирается конфиг; здесь только перехват.
 */
public final class ConfigReport implements AutoCloseable {

    private final Logger logger;
    private final List<String> issues = Collections.synchronizedList(new ArrayList<>());
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record != null && record.getLevel().intValue() >= Level.WARNING.intValue() && record.getMessage() != null) {
                issues.add(record.getMessage());
            }
        }
        @Override public void flush() { }
        @Override public void close() { }
    };

    private ConfigReport(Logger logger) {
        this.logger = logger;
        logger.addHandler(handler);
    }

    public static ConfigReport capture(Logger logger) {
        return new ConfigReport(logger);
    }

    /** Добавить проблему вручную и продублировать её в консоль. */
    public void warn(String message) {
        logger.warning(message); // попадёт в issues через handler
    }

    public List<String> issues() {
        synchronized (issues) {
            return List.copyOf(issues);
        }
    }

    @Override
    public void close() {
        logger.removeHandler(handler);
    }
}
