package org.qweyns.qweprotectstones.features.log;

import org.qweyns.qweprotectstones.QweProtectStones;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

public final class CriticalFileLogger {

    private final QweProtectStones plugin;
    private final Object lock = new Object();

    public CriticalFileLogger(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    private boolean isEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("settings.critical_log.enable", true);
    }

    private String folderName() {
        String raw = plugin.getConfigManager().getConfig().getString("settings.critical_log.folder", "logs");
        return raw == null || raw.isBlank() ? "logs" : raw.trim();
    }

    private String fileName() {
        String raw = plugin.getConfigManager().getConfig().getString("settings.critical_log.file-name", "critical.log");
        return raw == null || raw.isBlank() ? "critical.log" : raw.trim();
    }

    private long rotateSizeBytes() {
        int mb = Math.max(1, plugin.getConfigManager().getConfig().getInt("settings.critical_log.rotate-size-mb", 10));
        return (long) mb * 1024 * 1024;
    }

    public void log(String event, String detail) {
        if (!isEnabled()) return;

        String line = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date())
                + " [" + event + "] " + (detail == null ? "" : detail);
        plugin.getSchedulers().runAsync(() -> append(line));
    }

    private void append(String line) {
        synchronized (lock) {
            try {
                Path path = resolveFile();
                rotateIfNeeded(path.toFile());
                Files.writeString(path, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Не удалось записать событие в журнал критических операций", e);
            }
        }
    }

    private Path resolveFile() throws IOException {
        File folder = new File(plugin.getDataFolder(), folderName());
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("не удалось создать папку " + folder.getPath());
        }
        return new File(folder, fileName()).toPath();
    }

    private void rotateIfNeeded(File file) {
        if (!file.isFile() || file.length() < rotateSizeBytes()) return;

        File previous = new File(file.getParentFile(), file.getName() + ".old");
        if (previous.exists() && !previous.delete()) return;
        if (!file.renameTo(previous)) {
            plugin.getLogger().warning("Не удалось ротировать журнал критических операций: " + file.getName());
        }
    }
}
