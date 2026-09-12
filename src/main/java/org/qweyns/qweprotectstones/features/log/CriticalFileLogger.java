package org.qweyns.qweprotectstones.features.log;

import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.scheduler.Schedulers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/** Журнал критических операций: буфер в памяти, таймер флаша — по строке не дёргаем диск. */
public final class CriticalFileLogger {

    // потокобезопасный аналог SimpleDateFormat
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final long FLUSH_PERIOD_TICKS = 100L;

    private final QweProtectStones plugin;
    private final Object lock = new Object();

    private Schedulers.Task flushTask;
    private BufferedWriter writer;
    private File logFile;
    private boolean enabled;
    private long rotateSizeBytes;

    public CriticalFileLogger(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    /** Открывает журнал и запускает таймер флаша; вызывается из onEnable и /reload. */
    public void start() {
        synchronized (lock) {
            readSettings();
            closeWriter();
            openWriter();
        }
        if (flushTask != null) flushTask.cancel();
        flushTask = plugin.getSchedulers().runAsyncTimer(this::flush, FLUSH_PERIOD_TICKS, FLUSH_PERIOD_TICKS);
    }

    /** Сбрасывает буфер на диск, останавливает таймер и закрывает журнал; вызывается из onDisable. */
    public void stop() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        synchronized (lock) {
            closeWriter();
        }
    }

    private void readSettings() {
        enabled = plugin.getConfigManager().getConfig().getBoolean("settings.critical_log.enable", true);
        int mb = Math.max(1, plugin.getConfigManager().getConfig().getInt("settings.critical_log.rotate-size-mb", 10));
        rotateSizeBytes = (long) mb * 1024 * 1024;
    }

    private File resolveFile() throws IOException {
        String folderRaw = plugin.getConfigManager().getConfig().getString("settings.critical_log.folder", "logs");
        String fileRaw = plugin.getConfigManager().getConfig().getString("settings.critical_log.file-name", "critical.log");
        String folderName = folderRaw == null || folderRaw.isBlank() ? "logs" : folderRaw.trim();
        String fileName = fileRaw == null || fileRaw.isBlank() ? "critical.log" : fileRaw.trim();

        File folder = new File(plugin.getDataFolder(), folderName);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("не удалось создать папку " + folder.getPath());
        }
        return new File(folder, fileName);
    }

    public void log(String event, String detail) {
        if (!enabled) return;

        String line = TIMESTAMP.format(LocalDateTime.now())
                + " [" + event + "] " + (detail == null ? "" : detail);
        synchronized (lock) {
            if (writer == null) return;
            try {
                writer.write(line);
                writer.newLine();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Не удалось записать событие в журнал критических операций", e);
                // диск недоступен — закрываем, чтобы не спамить ошибкой на каждую строку
                closeWriter();
            }
        }
    }

    private void flush() {
        synchronized (lock) {
            if (writer == null) return;
            try {
                writer.flush();
                rotateIfNeeded();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Не удалось записать журнал критических операций на диск", e);
                closeWriter();
            }
        }
    }

    private void rotateIfNeeded() throws IOException {
        if (!logFile.isFile() || logFile.length() < rotateSizeBytes) return;

        File previous = new File(logFile.getParentFile(), logFile.getName() + ".old");
        writer.close();
        writer = null;
        if (previous.exists() && !previous.delete()) return;
        if (!logFile.renameTo(previous)) {
            plugin.getLogger().warning("Не удалось ротировать журнал критических операций: " + logFile.getName());
        }
        openWriter();
    }

    private void openWriter() {
        if (!enabled) return;
        try {
            logFile = resolveFile();
            writer = Files.newBufferedWriter(logFile.toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Журнал критических операций недоступен", e);
            writer = null;
        }
    }

    private void closeWriter() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
        }
    }
}
