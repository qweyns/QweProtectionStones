package org.qweyns.qweprotectstones.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

final class BukkitSchedulers implements Schedulers {

    private final Plugin plugin;

    BukkitSchedulers(Plugin plugin) {
        this.plugin = plugin;
    }

    private static Task wrap(BukkitTask task) {
        return task::cancel;
    }

    @Override
    public Task runNextTick(Runnable action) {
        return wrap(Bukkit.getScheduler().runTask(plugin, action));
    }

    @Override
    public Task runLater(Runnable action, long delayTicks) {
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, action, Math.max(1L, delayTicks)));
    }

    @Override
    public Task runTimer(Runnable action, long delayTicks, long periodTicks) {
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, action, Math.max(0L, delayTicks), Math.max(1L, periodTicks)));
    }

    @Override
    public Task runAsync(Runnable action) {
        return wrap(Bukkit.getScheduler().runTaskAsynchronously(plugin, action));
    }

    @Override
    public Task runAsyncTimer(Runnable action, long delayTicks, long periodTicks) {
        return wrap(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, action,
                Math.max(1L, delayTicks), Math.max(1L, periodTicks)));
    }

    @Override
    public void runAtLocation(Location location, Runnable action) {
        // на Paper весь мир в одном потоке
        if (Bukkit.isPrimaryThread()) action.run();
        else Bukkit.getScheduler().runTask(plugin, action);
    }

    @Override
    public void runAtLocationLater(Location location, Runnable action, long delayTicks) {
        runLater(action, delayTicks);
    }

    @Override
    public void runAtEntity(Entity entity, Runnable action) {
        runAtLocation(entity.getLocation(), action);
    }

    @Override
    public void runAtEntityLater(Entity entity, Runnable action, long delayTicks) {
        runLater(action, delayTicks);
    }

    @Override
    public Task runAtEntityTimer(Entity entity, Runnable action, long delayTicks, long periodTicks) {
        return runTimer(action, delayTicks, periodTicks);
    }

    @Override
    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    @Override
    public boolean isFolia() {
        return false;
    }
}
