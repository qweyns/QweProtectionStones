package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionText;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Текстовое оформление привата: название и сообщения на входе и выходе.
 *
 * <p>Три команды устроены одинаково — «показать текущее, задать новое или
 * стереть», — поэтому живут в одном классе, а различаются режимом.</p>
 */
public class DecorationSubCommand extends AbstractRegionSubCommand {

    public enum Mode {
        NAME("name", "region_name", Region::getDisplayName, Region::setDisplayName),
        GREETING("greeting", "region_greeting", Region::getGreeting, Region::setGreeting),
        FAREWELL("farewell", "region_farewell", Region::getFarewell, Region::setFarewell);

        private final String commandName;
        private final String messagePrefix;
        private final Function<Region, String> getter;
        private final BiConsumer<Region, String> setter;

        Mode(String commandName, String messagePrefix,
             Function<Region, String> getter, BiConsumer<Region, String> setter) {
            this.commandName = commandName;
            this.messagePrefix = messagePrefix;
            this.getter = getter;
            this.setter = setter;
        }
    }

    private final Mode mode;

    public DecorationSubCommand(QweProtectStones plugin, Mode mode) {
        super(plugin);
        this.mode = mode;
    }

    @Override
    public String permission() {
        return QweProtectStones.PERMISSION_PREFIX + ".name";
    }

    @Override
    public String name() {
        return mode.commandName;
    }

    @Override
    public List<String> aliases() {
        return mode == Mode.NAME ? List.of("rename", "title") : List.of();
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        Region region = regionWithTrust(player, plugin.getTunables().memberEditLevel());
        if (region == null) return;

        if (args.length == 0) {
            showCurrent(player, region);
            return;
        }

        String value = String.join(" ", args).trim();
        if (RegionText.isClearWord(value)) {
            mode.setter.accept(region, "");
            plugin.getRegionStorage().save(region);
            refreshViews(region);
            player.sendMessage(plugin.getLanguageManager().getMessage(mode.messagePrefix + "_cleared"));
            return;
        }

        mode.setter.accept(region, value);
        plugin.getRegionStorage().save(region);
        refreshViews(region);

        // Показываем то, что реально сохранилось: текст мог быть подрезан по длине.
        player.sendMessage(plugin.getLanguageManager().getMessage(mode.messagePrefix + "_set",
                "%value%", mode.getter.apply(region)));
    }

    private void showCurrent(Player player, Region region) {
        String current = mode.getter.apply(region);
        if (current.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage(mode.messagePrefix + "_usage",
                    "%limit%", String.valueOf(RegionText.MAX_LENGTH)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage(mode.messagePrefix + "_current",
                    "%value%", current));
        }
    }

    /** Название видно в голограмме и на карте — обновляем их сразу, а не после перезахода. */
    private void refreshViews(Region region) {
        if (mode != Mode.NAME) return;

        plugin.getHologramManager().createOrUpdateHologram(region);
        if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().update(region);
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().update(region);
    }

    @Override
    public List<String> complete(CommandSender sender, Player player, String[] args) {
        return args.length == 1 ? filter(List.of("clear"), args[0]) : List.of();
    }
}
