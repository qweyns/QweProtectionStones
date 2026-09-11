package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;

import java.util.List;

/**
 * Список друзей, которых плагин автоматически вписывает в каждый новый приват.
 * Три подкоманды с общей логикой собраны в один класс.
 */
public class AutoAddSubCommand extends AbstractRegionSubCommand {

    public enum Mode {
        /** Без аргумента — переключатель, с аргументом — добавление игрока. */
        TOGGLE_OR_ADD("autoadd"),
        REMOVE("autoremove"),
        LIST("autolist");

        private final String commandName;

        Mode(String commandName) {
            this.commandName = commandName;
        }
    }

    private final Mode mode;

    public AutoAddSubCommand(QweProtectStones plugin, Mode mode) {
        super(plugin);
        this.mode = mode;
    }

    @Override
    public String permission() {
        return QweProtectStones.PERMISSION_PREFIX + ".autoadd";
    }

    @Override
    public String name() {
        return mode.commandName;
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        switch (mode) {
            case TOGGLE_OR_ADD -> {
                if (args.length == 0) plugin.getAutoAddManager().toggle(player);
                else plugin.getAutoAddManager().addPlayer(player, args[0]);
            }
            case REMOVE -> {
                if (args.length == 0) player.sendMessage(plugin.getLanguageManager().getMessage("autoadd_usage"));
                else plugin.getAutoAddManager().removePlayer(player, args[0]);
            }
            case LIST -> plugin.getAutoAddManager().showList(player);
        }
    }

    @Override
    public List<String> complete(CommandSender sender, Player player, String[] args) {
        if (args.length != 1) return List.of();

        return switch (mode) {
            case TOGGLE_OR_ADD -> onlinePlayerNames(args[0]);
            case REMOVE -> player == null ? List.of() : filter(plugin.getAutoAddManager().getList(player), args[0]);
            case LIST -> List.of();
        };
    }
}
