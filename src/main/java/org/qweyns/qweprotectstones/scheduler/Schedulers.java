package org.qweyns.qweprotectstones.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public interface Schedulers {

    interface Task {
        void cancel();
    }

    Task runNextTick(Runnable action);

    Task runLater(Runnable action, long delayTicks);

    Task runTimer(Runnable action, long delayTicks, long periodTicks);

    Task runAsync(Runnable action);

    Task runAsyncTimer(Runnable action, long delayTicks, long periodTicks);

    void runAtLocation(Location location, Runnable action);

    void runAtLocationLater(Location location, Runnable action, long delayTicks);

    void runAtEntity(Entity entity, Runnable action);

    void runAtEntityLater(Entity entity, Runnable action, long delayTicks);

    Task runAtEntityTimer(Entity entity, Runnable action, long delayTicks, long periodTicks);

    void cancelAll();

    boolean isFolia();

    static Schedulers create(Plugin plugin) {
        return FoliaSchedulers.isFoliaServer()
                ? new FoliaSchedulers(plugin)
                : new BukkitSchedulers(plugin);
    }
}
