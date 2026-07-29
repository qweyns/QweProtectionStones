package org.qweyns.qweprotectstones.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Планировщик, одинаково работающий на Paper и на Folia.
 *
 * <p>На Folia нет единого главного потока: мир поделён на регионы, каждый со
 * своим потоком, и задачу нужно запускать в потоке того региона, где находятся
 * координаты. Весь код плагина ходит через этот интерфейс, поэтому в самих
 * менеджерах никаких проверок «а не Folia ли у нас» нет.</p>
 */
public interface Schedulers {

    /** Отменяемая задача — минимум, который нужен вызывающему коду. */
    interface Task {
        void cancel();
    }

    /** Выполнить в следующем тике глобального региона. */
    Task runNextTick(Runnable action);

    Task runLater(Runnable action, long delayTicks);

    Task runTimer(Runnable action, long delayTicks, long periodTicks);

    Task runAsync(Runnable action);

    Task runAsyncTimer(Runnable action, long delayTicks, long periodTicks);

    /**
     * Выполнить в потоке региона, которому принадлежит точка.
     * На Paper эквивалентно обычной задаче в главном потоке.
     */
    void runAtLocation(Location location, Runnable action);

    /** То же, но с задержкой: нужно, когда действие должно попасть в следующий тик региона. */
    void runAtLocationLater(Location location, Runnable action, long delayTicks);

    /** Выполнить в потоке, обслуживающем сущность (она может мигрировать между регионами). */
    void runAtEntity(Entity entity, Runnable action);

    void runAtEntityLater(Entity entity, Runnable action, long delayTicks);

    void cancelAll();

    boolean isFolia();

    /** Выбирает реализацию по тому, что реально доступно на сервере. */
    static Schedulers create(Plugin plugin) {
        return FoliaSchedulers.isFoliaServer()
                ? new FoliaSchedulers(plugin)
                : new BukkitSchedulers(plugin);
    }
}
