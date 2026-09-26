package org.qweyns.qweprotectstones.hooks;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.logging.Level;

public class DiscordSrvHook {

    private final java.util.Map<Class<?>, Method> sendCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Class<?>, Method> queueCache = new java.util.concurrent.ConcurrentHashMap<>();

    private final org.qweyns.qweprotectstones.QweProtectStones plugin;

    private Object discordSrv;
    private Class<?> dsrvClass;
    private boolean active;

    public DiscordSrvHook(org.qweyns.qweprotectstones.QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        if (Bukkit.getPluginManager().getPlugin("DiscordSRV") == null) return;

        try {
            dsrvClass = Class.forName("github.scarsz.discordsrv.DiscordSRV");
            discordSrv = dsrvClass.getMethod("getPlugin").invoke(null);
            active = discordSrv != null;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "DiscordSRV найден, но API недоступно — уведомления в Discord отключены.", t);
        }
    }

    public boolean isActive() {
        return active;
    }

    public void sendMessage(String message, String channelName) {
        if (!active || message == null || message.isBlank()) return;

        try {
            Object channel;
            if (channelName == null || channelName.isBlank()) {
                channel = dsrvClass.getMethod("getMainTextChannel").invoke(discordSrv);
            } else {
                channel = dsrvClass.getMethod("getOptionalTextChannel", String.class).invoke(discordSrv, channelName);
            }
            if (channel == null) return;

            Method send = sendCache.computeIfAbsent(channel.getClass(),
                    clazz -> methodOrNull(clazz, "sendMessage", CharSequence.class));
            if (send == null) return;
            Object action = send.invoke(channel, message);

            Method queue = queueCache.computeIfAbsent(action.getClass(),
                    clazz -> methodOrNull(clazz, "queue"));
            if (queue != null) queue.invoke(action);
        } catch (Throwable t) {
            if (t instanceof LinkageError || t instanceof NoSuchMethodException) {
                active = false;
                plugin.getLogger().log(Level.WARNING, "DiscordSRV: API несовместимо — интеграция отключена.", t);
            } else {
                // разовый сбой отправки не гасит уведомления навсегда
                plugin.getLogger().log(Level.WARNING, "DiscordSRV: не удалось отправить сообщение: " + t.getMessage());
            }
        }
    }

    private static Method methodOrNull(Class<?> clazz, String name, Class<?>... params) {
        try {
            return clazz.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
