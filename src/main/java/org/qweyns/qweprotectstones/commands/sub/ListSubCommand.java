package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;

import java.util.Comparator;
import java.util.List;

/** Список приватов игрока: свои и те, куда его вписали. */
public class ListSubCommand extends AbstractRegionSubCommand {

    public ListSubCommand(QweProtectStones plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "list";
    }

    @Override
    public List<String> aliases() {
        return List.of("regions", "my");
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        List<Region> owned = plugin.getRegionManager().getRegionsOf(player.getUniqueId());
        List<Region> accessible = plugin.getRegionManager().getAccessibleRegions(player.getUniqueId());

        player.sendMessage(plugin.getLanguageManager().getMessage("list_header",
                "%count%", String.valueOf(owned.size())));

        if (accessible.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("list_empty"));
            return;
        }

        accessible.stream()
                .sorted(Comparator.comparingLong(Region::getCreatedAt))
                .forEach(region -> player.sendMessage(line(player, region)));
    }

    private net.kyori.adventure.text.Component line(Player player, Region region) {
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        boolean owner = region.isOwner(player.getUniqueId());

        return plugin.getLanguageManager().getMessage(owner ? "list_line_own" : "list_line_shared",
                "%id%", region.getShortId(),
                "%type%", type != null ? type.displayName() : region.getTypeId(),
                "%world%", region.getWorldName(),
                "%x%", String.valueOf(region.getCoreX()),
                "%y%", String.valueOf(region.getCoreY()),
                "%z%", String.valueOf(region.getCoreZ()),
                "%owner%", region.getOwnerName(),
                "%durability%", String.valueOf(region.getDurability()),
                "%max%", String.valueOf(region.getMaxDurability()));
    }
}
