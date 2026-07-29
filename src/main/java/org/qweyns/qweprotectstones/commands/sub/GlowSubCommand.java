package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.util.List;

/** Постоянная подсветка границ привата частицами. */
public class GlowSubCommand extends AbstractRegionSubCommand {

    public GlowSubCommand(QweProtectStones plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "glow";
    }

    @Override
    public List<String> aliases() {
        return List.of("borders", "outline");
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        Region region = regionWithTrust(player, TrustLevel.ACCESS);
        if (region == null) return;

        boolean enabled = plugin.getVisualManager().toggleGlow(player, region);
        player.sendMessage(plugin.getLanguageManager().getMessage(enabled ? "glow_enabled" : "glow_disabled"));
    }
}
