package org.qweyns.qweprotectstones.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

final class FoliaSchedulers implements Schedulers {

    private final Plugin plugin;

    private final Object globalScheduler;
    private final Object regionScheduler;
    private final Object asyncScheduler;

    private final Method globalRun;
    private final Method globalRunDelayed;
    private final Method globalRunAtFixedRate;
    private final Method globalCancel;

    private final Method regionRun;
    private final Method regionRunDelayed;

    private final Method asyncRunNow;
    private final Method asyncRunAtFixedRate;
    private final Method asyncCancel;

    private final Method entityGetScheduler;
    private final Method entityRun;
    private final Method entityRunDelayed;
    private final Method entityRunAtFixedDelay;
    private final Method taskCancel;

    // повторяющиеся задачи: их нельзя отменить через планировщики сервера разом
    private final java.util.Set<Task> repeatingTasks = ConcurrentHashMap.newKeySet();

    static boolean isFoliaServer() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    FoliaSchedulers(Plugin plugin) {
        this.plugin = plugin;
        try {
            Class<?> serverClass = Bukkit.getServer().getClass();
            globalScheduler = serverClass.getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            regionScheduler = serverClass.getMethod("getRegionScheduler").invoke(Bukkit.getServer());
            asyncScheduler = serverClass.getMethod("getAsyncScheduler").invoke(Bukkit.getServer());

            Class<?> globalClass = globalScheduler.getClass();
            globalRun = findMethod(globalClass, "run", Plugin.class, Consumer.class);
            globalRunDelayed = findMethod(globalClass, "runDelayed", Plugin.class, Consumer.class, long.class);
            globalRunAtFixedRate = findMethod(globalClass, "runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            globalCancel = findMethod(globalClass, "cancelTasks", Plugin.class);

            Class<?> regionClass = regionScheduler.getClass();
            regionRun = findMethod(regionClass, "run", Plugin.class, Location.class, Consumer.class);
            regionRunDelayed = findMethod(regionClass, "runDelayed", Plugin.class, Location.class, Consumer.class, long.class);

            Class<?> asyncClass = asyncScheduler.getClass();
            asyncRunNow = findMethod(asyncClass, "runNow", Plugin.class, Consumer.class);
            asyncRunAtFixedRate = findMethod(asyncClass, "runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);
            asyncCancel = findMethod(asyncClass, "cancelTasks", Plugin.class);

            entityGetScheduler = Entity.class.getMethod("getScheduler");
            Class<?> entitySchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            entityRun = findMethod(entitySchedulerClass, "run", Plugin.class, Consumer.class, Runnable.class);
            entityRunDelayed = findMethod(entitySchedulerClass, "runDelayed",
                    Plugin.class, Consumer.class, Runnable.class, long.class);
            entityRunAtFixedDelay = findMethod(entitySchedulerClass, "runAtFixedDelay",
                    Plugin.class, Consumer.class, Runnable.class, long.class, long.class);

            taskCancel = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask").getMethod("cancel");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Не удалось подключиться к планировщику Folia", e);
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... params) throws NoSuchMethodException {
        try {
            return owner.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            for (Class<?> iface : owner.getInterfaces()) {
                try {
                    return findMethod(iface, name, params);
                } catch (NoSuchMethodException ignored) {
                    // пробуем следующий интерфейс
                }
            }
            throw e;
        }
    }

    private Task wrap(Object foliaTask) {
        if (foliaTask == null) return () -> { };
        return () -> {
            try {
                taskCancel.invoke(foliaTask);
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().log(Level.WARNING, "Не удалось отменить задачу Folia", e);
            }
        };
    }

    /** Обёртка повторяющейся задачи: cancelAll снимает её из трекинга. */
    private Task wrapRepeating(Object foliaTask) {
        if (foliaTask == null) return () -> { };

        Task plain = wrap(foliaTask);
        Task tracked = new Task() {
            @Override
            public void cancel() {
                repeatingTasks.remove(this);
                plain.cancel();
            }
        };
        repeatingTasks.add(tracked);
        return tracked;
    }

    private Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка планировщика Folia: " + method.getName(), e);
            return null;
        }
    }

    private static Consumer<Object> ignoreTask(Runnable action) {
        return task -> action.run();
    }

    @Override
    public Task runNextTick(Runnable action) {
        return wrap(invoke(globalRun, globalScheduler, plugin, ignoreTask(action)));
    }

    @Override
    public Task runLater(Runnable action, long delayTicks) {
        // Folia не принимает нулевую задержку.
        return wrap(invoke(globalRunDelayed, globalScheduler, plugin, ignoreTask(action), Math.max(1L, delayTicks)));
    }

    @Override
    public Task runTimer(Runnable action, long delayTicks, long periodTicks) {
        return wrapRepeating(invoke(globalRunAtFixedRate, globalScheduler, plugin, ignoreTask(action),
                Math.max(1L, delayTicks), Math.max(1L, periodTicks)));
    }

    @Override
    public Task runAsync(Runnable action) {
        return wrap(invoke(asyncRunNow, asyncScheduler, plugin, ignoreTask(action)));
    }

    @Override
    public Task runAsyncTimer(Runnable action, long delayTicks, long periodTicks) {
        // Асинхронный планировщик Folia работает во времени, а не в тиках.
        long delayMs = Math.max(1L, delayTicks) * 50L;
        long periodMs = Math.max(1L, periodTicks) * 50L;
        return wrapRepeating(invoke(asyncRunAtFixedRate, asyncScheduler, plugin, ignoreTask(action),
                delayMs, periodMs, TimeUnit.MILLISECONDS));
    }

    @Override
    public void runAtLocation(Location location, Runnable action) {
        invoke(regionRun, regionScheduler, plugin, location, ignoreTask(action));
    }

    @Override
    public void runAtLocationLater(Location location, Runnable action, long delayTicks) {
        invoke(regionRunDelayed, regionScheduler, plugin, location, ignoreTask(action), Math.max(1L, delayTicks));
    }

    @Override
    public void runAtEntity(Entity entity, Runnable action) {
        Object entityScheduler = invoke(entityGetScheduler, entity);
        if (entityScheduler == null) return;

        // Третий аргумент — что делать, если сущность исчезла до запуска.
        invoke(entityRun, entityScheduler, plugin, ignoreTask(action), (Runnable) () -> { });
    }

    @Override
    public void runAtEntityLater(Entity entity, Runnable action, long delayTicks) {
        Object entityScheduler = invoke(entityGetScheduler, entity);
        if (entityScheduler == null) return;

        invoke(entityRunDelayed, entityScheduler, plugin, ignoreTask(action),
                (Runnable) () -> { }, Math.max(1L, delayTicks));
    }

    @Override
    public Task runAtEntityTimer(Entity entity, Runnable action, long delayTicks, long periodTicks) {
        Object entityScheduler = invoke(entityGetScheduler, entity);
        if (entityScheduler == null) return () -> { };

        // таймеры меню обязаны тикать в потоке игрока, а не в глобальном
        return wrapRepeating(invoke(entityRunAtFixedDelay, entityScheduler, plugin, ignoreTask(action),
                (Runnable) () -> { }, Math.max(1L, delayTicks), Math.max(1L, periodTicks)));
    }

    @Override
    public void cancelAll() {
        invoke(globalCancel, globalScheduler, plugin);
        invoke(asyncCancel, asyncScheduler, plugin);
        for (Task task : new ArrayList<>(repeatingTasks)) task.cancel();
        repeatingTasks.clear();
    }

    @Override
    public boolean isFolia() {
        return true;
    }
}
