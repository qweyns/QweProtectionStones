package org.qweyns.qweprotectstones.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public interface SubCommand {

    String name();

    default List<String> aliases() {
        return List.of();
    }

    default String permission() {
        return null;
    }

    default boolean playerOnly() {
        return true;
    }

    default String helpKey() {
        return "help_" + name();
    }

    void execute(CommandSender sender, Player player, String[] args);

    default List<String> complete(CommandSender sender, Player player, String[] args) {
        return List.of();
    }
}
