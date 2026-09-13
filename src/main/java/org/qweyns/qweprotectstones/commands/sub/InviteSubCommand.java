package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.TrustLevel;
import org.qweyns.qweprotectstones.features.invite.InviteManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InviteSubCommand extends AbstractRegionSubCommand {

    public enum Mode {
        INVITE("invite"),
        ACCEPT("accept"),
        DENY("deny");

        private final String commandName;

        Mode(String commandName) {
            this.commandName = commandName;
        }
    }

    private final Mode mode;

    public InviteSubCommand(QweProtectStones plugin, Mode mode) {
        super(plugin);
        this.mode = mode;
    }

    @Override
    public String permission() {
        return QweProtectStones.PERMISSION_PREFIX + ".invite";
    }

    @Override
    public String name() {
        return mode.commandName;
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        switch (mode) {
            case INVITE -> invite(player, args);
            case ACCEPT -> respond(player, true);
            case DENY -> respond(player, false);
        }
    }

    private void invite(Player player, String[] args) {
        Region region = regionWithTrust(player, plugin.getTunables().memberEditLevel());
        if (region == null) return;

        if (args.length == 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage("invite_usage"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("player_not_online", "%player%", args[0]));
            return;
        }
        if (target.equals(player)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("invite_self"));
            return;
        }
        if (region.isOwner(target.getUniqueId()) || region.getMember(target.getUniqueId()).isPresent()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("invite_already_member", "%player%", target.getName()));
            return;
        }

        TrustLevel level = TrustLevel.BUILD;
        if (args.length > 1) {
            var parsed = TrustLevel.parse(args[1]);
            if (parsed.isEmpty() || parsed.get() == TrustLevel.OWNER) {
                player.sendMessage(plugin.getLanguageManager().getMessage("trust_unknown_level", "%levels%", levelNames()));
                return;
            }
            level = parsed.get();
        }

        TrustLevel actorTrust = plugin.getProtectionService().trustOf(region, player);
        if (actorTrust != null && !actorTrust.atLeast(level)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("trust_too_high"));
            return;
        }

        if (!plugin.getInviteManager().invite(player, target, region, level)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("invite_already_sent", "%player%", target.getName()));
            return;
        }

        String levelName = plugin.getLanguageManager().rawTemplate("trust_" + level.key());
        player.sendMessage(plugin.getLanguageManager().getMessage("invite_sent",
                "%player%", target.getName(), "%level%", levelName));
        plugin.getLanguageManager().sendList(target, "invite_received",
                "%player%", player.getName(),
                "%level%", levelName,
                "%command%", plugin.getConfigManager().getCommandName(),
                "%seconds%", String.valueOf(plugin.getInviteManager().expireSecondsForMessage()));

        plugin.getTunables().inviteReceived().playTo(target);
    }

    private void respond(Player player, boolean accepted) {
        InviteManager.Invite invite = plugin.getInviteManager().consume(player);
        if (invite == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("invite_none"));
            return;
        }

        Region region = plugin.getRegionManager().getById(invite.regionId());
        if (region == null) {
            // приват могли снести, пока приглашение висело
            player.sendMessage(plugin.getLanguageManager().getMessage("invite_expired"));
            return;
        }

        Player inviter = Bukkit.getPlayer(invite.inviterId());
        if (!accepted) {
            player.sendMessage(plugin.getLanguageManager().getMessage("invite_denied"));
            if (inviter != null) {
                inviter.sendMessage(plugin.getLanguageManager().getMessage("invite_denied_by", "%player%", player.getName()));
            }
            return;
        }

        // вето на принятие приглашения — как и на прямую выдачу доверия
        if (org.qweyns.qweprotectstones.regions.event.RegionEvents.fireMemberChange(region, inviter,
                player.getUniqueId(), player.getName(),
                org.qweyns.qweprotectstones.regions.event.RegionMemberChangeEvent.Action.TRUST,
                invite.level())) return;

        region.setMember(player.getUniqueId(), player.getName(), invite.level());
        plugin.getRegionStorage().save(region);

        String levelName = plugin.getLanguageManager().rawTemplate("trust_" + invite.level().key());
        player.sendMessage(plugin.getLanguageManager().getMessage("invite_accepted",
                "%owner%", region.getOwnerName(), "%level%", levelName));

        if (inviter != null) {
            inviter.sendMessage(plugin.getLanguageManager().getMessage("invite_accepted_by", "%player%", player.getName()));
        }
    }

    private String levelNames() {
        List<String> names = new ArrayList<>();
        for (TrustLevel level : TrustLevel.grantable()) names.add(level.key());
        return String.join(", ", names);
    }

    @Override
    public List<String> complete(CommandSender sender, Player player, String[] args) {
        if (mode != Mode.INVITE) return List.of();

        if (args.length == 1) return onlinePlayerNames(args[0]);
        if (args.length == 2) {
            List<String> levels = new ArrayList<>();
            for (TrustLevel level : TrustLevel.grantable()) levels.add(level.key());
            return filter(levels, args[1].toLowerCase(Locale.ROOT));
        }
        return List.of();
    }
}
