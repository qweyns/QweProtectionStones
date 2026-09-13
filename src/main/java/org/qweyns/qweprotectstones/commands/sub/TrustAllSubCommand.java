package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.qweyns.qweprotectstones.regions.event.RegionEvents;
import org.qweyns.qweprotectstones.regions.event.RegionMemberChangeEvent;

public class TrustAllSubCommand extends AbstractRegionSubCommand {

    private final boolean granting;

    public TrustAllSubCommand(QweProtectStones plugin, boolean granting) {
        super(plugin);
        this.granting = granting;
    }

    @Override
    public String permission() {
        return QweProtectStones.PERMISSION_PREFIX + ".trust";
    }

    @Override
    public String name() {
        return granting ? "trustall" : "untrustall";
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        List<Region> owned = plugin.getRegionManager().getRegionsOf(player.getUniqueId());
        if (owned.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("list_empty"));
            return;
        }

        if (args.length == 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage(granting ? "trustall_usage" : "untrustall_usage"));
            return;
        }

        OfflinePlayer target = resolvePlayer(args[0]);
        if (target == null || target.getUniqueId() == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("player_not_found", "%player%", args[0]));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("trust_owner_immutable"));
            return;
        }

        TrustLevel level = TrustLevel.BUILD;
        if (granting && args.length > 1) {
            var parsed = TrustLevel.parse(args[1]);
            if (parsed.isEmpty() || parsed.get() == TrustLevel.OWNER) {
                player.sendMessage(plugin.getLanguageManager().getMessage("trust_unknown_level", "%levels%", levelNames()));
                return;
            }
            level = parsed.get();
        }

        String targetName = target.getName() != null ? target.getName() : args[0];
        int changed = 0;

        for (Region region : owned) {
            if (granting) {
                if (RegionEvents.fireMemberChange(region, player, target.getUniqueId(), targetName,
                        RegionMemberChangeEvent.Action.TRUST, level)) continue;
                region.setMember(target.getUniqueId(), targetName, level);
                changed++;
            } else if (region.getMember(target.getUniqueId()).isPresent()) {
                if (RegionEvents.fireMemberChange(region, player, target.getUniqueId(), targetName,
                        RegionMemberChangeEvent.Action.UNTRUST, null)) continue;
                region.removeMember(target.getUniqueId());
                changed++;
            }
            plugin.getRegionStorage().save(region);
        }

        if (changed == 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage("trust_not_member", "%player%", targetName));
            return;
        }

        String levelName = plugin.getLanguageManager().rawTemplate("trust_" + level.key());
        player.sendMessage(plugin.getLanguageManager().getMessage(granting ? "trustall_done" : "untrustall_done",
                "%player%", targetName, "%count%", String.valueOf(changed), "%level%", levelName));

        Player online = target.getPlayer();
        if (online != null && granting) {
            online.sendMessage(plugin.getLanguageManager().getMessage("trustall_target",
                    "%owner%", player.getName(), "%count%", String.valueOf(changed), "%level%", levelName));
        }
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
        if (args.length == 1) return onlinePlayerNames(args[0]);

        if (args.length == 2 && granting) {
            List<String> levels = new ArrayList<>();
            for (TrustLevel level : TrustLevel.grantable()) levels.add(level.key());
            return filter(levels, args[1].toLowerCase(Locale.ROOT));
        }
        return List.of();
    }
}
