package org.qweyns.qweprotectstones.features.backup;

import org.qweyns.qweprotectstones.QweProtectStones;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class BackupTask {

    private final QweProtectStones plugin;

    public BackupTask(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfigManager().getConfig().getBoolean("backup.enable", false)) return;

        long intervalMinutes = Math.max(10, plugin.getConfigManager().getConfig()
                .getLong("backup.interval-minutes", 720L));
        long intervalTicks = TimeUnit.MINUTES.toSeconds(intervalMinutes) * 20L;

        plugin.getSchedulers().runTimer(this::run, intervalTicks, intervalTicks);
        plugin.getLogger().info("Автобэкап включён: каждые " + intervalMinutes + " мин., хранится копий — "
                + keepCount() + ".");
    }

    public void run() {
        int keep = keepCount();

        plugin.getSchedulers().runAsync(() -> {
            try {
                plugin.getRegionExporter().export();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Автобэкап: выгрузка не удалась", e);
                return;
            }
            if (keep > 0) rotate(keep);
        });
    }

    private void rotate(int keep) {
        File folder = new File(plugin.getDataFolder(), "exports");
        File[] files = folder.listFiles((dir, name) -> name.startsWith("regions_") && name.endsWith(".json"));
        if (files == null || files.length <= keep) return;

        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort(Comparator.comparingLong(File::lastModified));

        int toDelete = sorted.size() - keep;
        for (int i = 0; i < toDelete; i++) {
            if (!sorted.get(i).delete()) {
                plugin.getLogger().warning("Автобэкап: не удалось удалить " + sorted.get(i).getName());
            }
        }
    }

    private int keepCount() {
        return Math.max(0, plugin.getConfigManager().getConfig().getInt("backup.keep-count", 10));
    }
}
