package org.qweyns.qweprotectstones.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.qweyns.qweprotectstones.QweProtectStones;

import java.lang.reflect.Method;
import java.util.logging.Level;

public final class CommandRegistrar {

    private CommandRegistrar() {
    }

    public static boolean register(QweProtectStones plugin, Command command) {
        CommandMap map = resolveCommandMap(plugin);
        if (map == null) return false;

        // при конфликте имён команда остаётся как /qweprotectstones:<имя>

        boolean registered = map.register("qweprotectstones", command);
        if (!registered) {
            plugin.getLogger().warning("Команда '" + command.getName()
                    + "' уже занята другим плагином — используйте /qweprotectstones:" + command.getName()
                    + " или измените имя в config.yml.");
        }
        return true;
    }

    public static void unregister(QweProtectStones plugin, Command command) {
        CommandMap map = resolveCommandMap(plugin);
        if (map == null) return;

        command.unregister(map);
    }

    private static CommandMap resolveCommandMap(QweProtectStones plugin) {
        try {
            Method method = Bukkit.getServer().getClass().getMethod("getCommandMap");
            return (CommandMap) method.invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException | ClassCastException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось получить CommandMap сервера — команды привата недоступны.", e);
            return null;
        }
    }
}
