package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionMember;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Список тех, у кого есть доступ к привату, с их уровнями. */
public class MembersSubCommand extends AbstractRegionSubCommand {

    public MembersSubCommand(QweProtectStones plugin) {
        super(plugin);
    }

    @Override
    public String permission() {
        return QweProtectStones.PERMISSION_PREFIX + ".members";
    }

    @Override
    public String name() {
        return "members";
    }

    @Override
    public List<String> aliases() {
        return List.of("trustlist", "who");
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        Region region = regionWithTrust(player, TrustLevel.ACCESS);
        if (region == null) return;

        player.sendMessage(plugin.getLanguageManager().getMessage("members_header",
                "%owner%", region.getOwnerName()));

        if (region.getMemberCount() == 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage("members_empty"));
            return;
        }

        List<RegionMember> members = new ArrayList<>(region.getMembers());
        // Сначала самые доверенные — так список читается сверху вниз.
        members.sort(Comparator.comparingInt((RegionMember m) -> -m.trust().weight())
                .thenComparing(RegionMember::displayName, String.CASE_INSENSITIVE_ORDER));

        for (RegionMember member : members) {
            player.sendMessage(plugin.getLanguageManager().getMessage("members_line",
                    "%player%", member.displayName(),
                    "%level%", plugin.getLanguageManager().getRawMessage("trust_" + member.trust().key())));
        }
    }
}
