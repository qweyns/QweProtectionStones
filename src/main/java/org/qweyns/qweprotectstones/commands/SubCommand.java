package org.qweyns.qweprotectstones.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** Одна подкоманда /region. */
public interface SubCommand {

    String name();

    /** Дополнительные имена, по которым подкоманда тоже вызывается. */
    default List<String> aliases() {
        return List.of();
    }

    /** Право доступа или {@code null}, если команда доступна всем. */
    default String permission() {
        return null;
    }

    /** Требуется ли игрок (а не консоль). */
    default boolean playerOnly() {
        return true;
    }

    /** Ключ описания в языковом файле: {@code help_<key>}. */
    default String helpKey() {
        return "help_" + name();
    }

    /** @param args аргументы без имени самой подкоманды */
    void execute(CommandSender sender, Player player, String[] args);

    default List<String> complete(CommandSender sender, Player player, String[] args) {
        return List.of();
    }
}
