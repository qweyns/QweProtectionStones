package org.qweyns.qweprotectstones.commands.admin;

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

/** Админ-команды состава привата: баны, участники, доверия. */
class MemberAdminCommands {

    private final QweProtectStones plugin;
    private final AdminSupport support;

    MemberAdminCommands(QweProtectStones plugin, AdminSupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    void ban(CommandSender sender, Player player, String[] args, boolean add) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(
                    add ? "admin_ban_usage" : "admin_unban_usage"));
            return;
        }

        Region region = support.resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        OfflinePlayer target = support.resolvePlayer(args[2]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("player_not_found", "%player%", args[2]));
            return;
        }
        String targetName = target.getName() != null ? target.getName() : args[2];

        if (add) {
            if (RegionEvents.fireMemberChange(region, player, target.getUniqueId(), targetName,
                    RegionMemberChangeEvent.Action.BAN, null)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_action_cancelled"));
                return;
            }
            region.ban(target.getUniqueId(), targetName);
        } else {
            if (!region.isBanned(target.getUniqueId())) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_not_banned", "%player%", targetName));
                return;
            }
            if (RegionEvents.fireMemberChange(region, player, target.getUniqueId(), targetName,
                    RegionMemberChangeEvent.Action.UNBAN, null)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_action_cancelled"));
                return;
            }
            region.unban(target.getUniqueId());
        }

        plugin.getRegionStorage().save(region);
        support.auditLog(region, sender, add ? "ban" : "unban", "player=" + targetName);
        sender.sendMessage(plugin.getLanguageManager().getMessage(
                add ? "admin_ban_done" : "admin_unban_done",
                "%id%", region.getShortId(), "%player%", targetName));
    }

    void members(CommandSender sender, Player player, String[] args) {
        Region region = support.resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_members_header",
                "%id%", region.getShortId(), "%owner%", region.getOwnerName()));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_members_owner", "%player%", region.getOwnerName()));

        if (region.getMemberCount() == 0) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_members_empty"));
        } else {
            for (RegionMember member : region.getMembers()) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_members_entry",
                        "%player%", member.name(),
                        "%level%", plugin.getLanguageManager().rawTemplate("trust_" + member.trust().key())));
            }
        }

        for (var entry : region.getBannedPlayers().values()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_members_banned", "%player%", entry));
        }
    }

    void trust(CommandSender sender, Player player, String[] args, boolean grant) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(
                    grant ? "admin_trust_usage" : "admin_untrust_usage"));
            return;
        }

        Region region = support.resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        OfflinePlayer target = support.resolvePlayer(args[2]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("player_not_found", "%player%", args[2]));
            return;
        }
        if (region.isOwner(target.getUniqueId())) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("trust_owner_immutable"));
            return;
        }
        String targetName = target.getName() != null ? target.getName() : args[2];

        if (grant) {
            TrustLevel level = TrustLevel.BUILD;
            if (args.length > 3) {
                var parsed = TrustLevel.parse(args[3]);
                if (parsed.isEmpty() || parsed.get() == TrustLevel.OWNER) {
                    List<String> names = new ArrayList<>();
                    for (TrustLevel l : TrustLevel.grantable()) names.add(l.key());
                    sender.sendMessage(plugin.getLanguageManager().getMessage("trust_unknown_level",
                            "%levels%", String.join(", ", names)));
                    return;
                }
                level = parsed.get();
            }
            if (RegionEvents.fireMemberChange(region, player, target.getUniqueId(), targetName,
                    RegionMemberChangeEvent.Action.TRUST, level)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_action_cancelled"));
                return;
            }
            region.setMember(target.getUniqueId(), targetName, level);
        } else {
            if (region.getMember(target.getUniqueId()).isEmpty()) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("trust_not_member", "%player%", targetName));
                return;
            }
            if (RegionEvents.fireMemberChange(region, player, target.getUniqueId(), targetName,
                    RegionMemberChangeEvent.Action.UNTRUST, null)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_action_cancelled"));
                return;
            }
            region.removeMember(target.getUniqueId());
        }

        plugin.getRegionStorage().save(region);
        support.auditLog(region, sender, grant ? "trust" : "untrust", "player=" + targetName);
        sender.sendMessage(plugin.getLanguageManager().getMessage(
                grant ? "admin_trust_done" : "admin_untrust_done",
                "%id%", region.getShortId(), "%player%", targetName,
                "%level%", plugin.getLanguageManager().rawTemplate("trust_build")));
    }
}
