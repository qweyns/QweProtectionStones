package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionMember;
import org.qweyns.qweprotectstones.regions.TrustLevel;
import org.qweyns.qweprotectstones.regions.event.RegionEvents;
import org.qweyns.qweprotectstones.regions.event.RegionMemberChangeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TrustSubCommand extends AbstractRegionSubCommand {

    private final boolean granting;

    public TrustSubCommand(QweProtectStones plugin, boolean granting) {
        super(plugin);
        this.granting = granting;
    }

    @Override
    public String permission() {
        return QweProtectStones.PERMISSION_PREFIX + ".trust";
    }

    @Override
    public String name() {
        return granting ? "trust" : "untrust";
    }

    @Override
    public List<String> aliases() {
        return granting ? List.of("add", "allow") : List.of("remove", "deny", "kick");
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        Region region = regionWithTrust(player, plugin.getTunables().memberEditLevel());
        if (region == null) return;

        if (args.length == 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage(
                    granting ? "trust_usage" : "untrust_usage"));
            return;
        }

        OfflinePlayer target = resolvePlayer(args[0]);
        if (target == null || target.getUniqueId() == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("player_not_found", "%player%", args[0]));
            return;
        }
        if (region.isOwner(target.getUniqueId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("trust_owner_immutable"));
            return;
        }

        if (granting) {
            grant(player, region, target, args);
        } else {
            revoke(player, region, target);
        }
    }

    private void grant(Player player, Region region, OfflinePlayer target, String[] args) {
        TrustLevel level = TrustLevel.BUILD;
        if (args.length > 1) {
            var parsed = TrustLevel.parse(args[1]);
            if (parsed.isEmpty() || parsed.get() == TrustLevel.OWNER) {
                player.sendMessage(plugin.getLanguageManager().getMessage("trust_unknown_level",
                        "%levels%", levelNames()));
                return;
            }
            level = parsed.get();
        }

        TrustLevel actorTrust = plugin.getProtectionService().trustOf(region, player);
        if (actorTrust != null && !actorTrust.atLeast(level)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("trust_too_high"));
            return;
        }

        String targetName = target.getName() != null ? target.getName() : args[0];
        region.setMember(target.getUniqueId(), targetName, level);
        plugin.getRegionStorage().save(region);

        String levelName = plugin.getLanguageManager().rawTemplate("trust_" + level.key());
        player.sendMessage(plugin.getLanguageManager().getMessage("trust_granted",
                "%player%", targetName, "%level%", levelName));

        Player online = target.getPlayer();
        if (online != null) {
            online.sendMessage(plugin.getLanguageManager().getMessage("trust_granted_target",
                    "%owner%", region.getOwnerName(), "%level%", levelName));
        }
    }

    private void revoke(Player player, Region region, OfflinePlayer target) {
        String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString();

        if (region.getMember(target.getUniqueId()).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("trust_not_member", "%player%", targetName));
            return;
        }
        if (RegionEvents.fireMemberChange(region, player, target.getUniqueId(), targetName,
                RegionMemberChangeEvent.Action.UNTRUST, null)) return;

        region.removeMember(target.getUniqueId());

        plugin.getRegionStorage().save(region);
        player.sendMessage(plugin.getLanguageManager().getMessage("trust_revoked", "%player%", targetName));
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && cached.getUniqueId() != null ? cached : null;
    }

    private String levelNames() {
        List<String> names = new ArrayList<>();
        for (TrustLevel level : TrustLevel.grantable()) names.add(level.key());
        return String.join(", ", names);
    }

    @Override
    public List<String> complete(CommandSender sender, Player player, String[] args) {
        if (args.length == 1) {
            if (granting) return onlinePlayerNames(args[0]);

            Region region = player == null ? null : plugin.getRegionManager().getRegionAt(player.getLocation());
            if (region == null) return List.of();

            List<String> members = new ArrayList<>();
            for (RegionMember member : region.getMembers()) members.add(member.displayName());
            return filter(members, args[0]);
        }

        if (args.length == 2 && granting) {
            List<String> levels = new ArrayList<>();
            for (TrustLevel level : TrustLevel.grantable()) levels.add(level.key());
            return filter(levels, args[1].toLowerCase(Locale.ROOT));
        }
        return List.of();
    }
}
