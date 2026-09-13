package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class InfoSubCommand extends AbstractRegionSubCommand {

    public InfoSubCommand(QweProtectStones plugin) {
        super(plugin);
    }

    @Override
    public String permission() {
        return QweProtectStones.PERMISSION_PREFIX + ".info";
    }

    @Override
    public String name() {
        return "info";
    }

    @Override
    public List<String> aliases() {
        return List.of("i", "who");
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        Region region = regionUnderFeet(player);
        if (region == null) return;

        TrustLevel trust = plugin.getProtectionService().trustOf(region, player);

        // чужие территории — отдельное право, как /rg info

        if (trust == null && !player.hasPermission(QweProtectStones.PERMISSION_PREFIX + ".info.others")) {
            player.sendMessage(plugin.getLanguageManager().getMessage("no_permission"));
            return;
        }

        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        RegionBounds bounds = region.getBounds();

        String trustName = trust == null
                ? plugin.getLanguageManager().rawTemplate("trust_none")
                : plugin.getLanguageManager().rawTemplate("trust_" + trust.key());

        player.sendMessage(plugin.getLanguageManager().getMessage("info_header"));
        player.sendMessage(plugin.getLanguageManager().getMessage("info_owner", "%owner%", region.getOwnerName()));
        player.sendMessage(plugin.getLanguageManager().getMessage("info_type",
                "%type%", type != null ? type.displayName() : region.getTypeId()));
        player.sendMessage(plugin.getLanguageManager().getMessage("info_size",
                "%x%", String.valueOf(bounds.sizeX()),
                "%z%", String.valueOf(bounds.sizeZ()),
                "%blocks%", String.valueOf(bounds.area())));
        player.sendMessage(plugin.getLanguageManager().getMessage("info_durability",
                "%current%", String.valueOf(region.getDurability()),
                "%max%", String.valueOf(region.getMaxDurability())));
        player.sendMessage(plugin.getLanguageManager().getMessage("info_members",
                "%count%", String.valueOf(region.getMemberCount())));
        player.sendMessage(plugin.getLanguageManager().getMessage("info_your_trust", "%trust%", trustName));
        player.sendMessage(plugin.getLanguageManager().getMessage("info_created",
                "%date%", new SimpleDateFormat("dd.MM.yyyy").format(new Date(region.getCreatedAt()))));

        if (plugin.getPenaltyManager().hasPenalty(region)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("info_penalty",
                    "%multiplier%", String.valueOf(plugin.getPenaltyManager().getPenaltyMultiplier())));
        }

        if (trust == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("info_bounds",
                    "%min%", bounds.minX() + ", " + bounds.minY() + ", " + bounds.minZ(),
                    "%max%", bounds.maxX() + ", " + bounds.maxY() + ", " + bounds.maxZ()));
            for (var member : region.getMembers()) {
                player.sendMessage(plugin.getLanguageManager().getMessage("info_member_entry",
                        "%name%", member.displayName(),
                        "%level%", plugin.getLanguageManager().rawTemplate("trust_" + member.trust().key())));
            }
            if (region.getAttackCount() > 0) {
                player.sendMessage(plugin.getLanguageManager().getMessage("info_attacks",
                        "%count%", String.valueOf(region.getAttackCount()),
                        "%date%", new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(region.getLastAttackAt())),
                        "%attacker%", region.getLastAttackerName()));
            }
        }

        plugin.getVisualManager().showBoundary(region, "info");
    }
}
