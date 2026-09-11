package org.qweyns.qweprotectstones.hooks;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.logging.Level;

public class DiscordSrvHook {

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

            Object action = channel.getClass().getMethod("sendMessage", CharSequence.class).invoke(channel, message);
            Method queue = action.getClass().getMethod("queue");
            queue.invoke(action);
        } catch (Throwable t) {
            active = false;
            plugin.getLogger().log(Level.WARNING, "DiscordSRV: не удалось отправить сообщение — интеграция отключена.", t);
        }
    }
}
