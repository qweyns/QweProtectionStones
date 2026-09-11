package org.qweyns.qweprotectstones.commands.sub;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.event.RegionEvents;
import org.qweyns.qweprotectstones.regions.event.RegionMemberChangeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BanSubCommand extends AbstractRegionSubCommand {

    public enum Mode {
        BAN("ban"),
        UNBAN("unban"),
        LIST("banlist");

        private final String commandName;

        Mode(String commandName) {
            this.commandName = commandName;
        }
    }

    private final Mode mode;

    public BanSubCommand(QweProtectStones plugin, Mode mode) {
        super(plugin);
        this.mode = mode;
    }

    @Override
    public String permission() {
        return QweProtectStones.PERMISSION_PREFIX + ".ban";
    }

    @Override
    public String name() {
        return mode.commandName;
    }

    @Override
    public void execute(CommandSender sender, Player player, String[] args) {
        Region region = regionWithTrust(player, plugin.getTunables().memberEditLevel());
        if (region == null) return;

        switch (mode) {
            case BAN -> ban(player, region, args);
            case UNBAN -> unban(player, region, args);
            case LIST -> list(player, region);
        }
    }

    private void ban(Player player, Region region, String[] args) {
        if (args.length == 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage("ban_usage"));
            return;
        }

        OfflinePlayer target = resolvePlayer(args[0]);
        if (target == null || target.getUniqueId() == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("player_not_found", "%player%", args[0]));
            return;
        }
        if (region.isOwner(target.getUniqueId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("ban_owner"));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("ban_self"));
            return;
        }

        String targetName = target.getName() != null ? target.getName() : args[0];
        region.ban(target.getUniqueId(), targetName);
        plugin.getRegionStorage().save(region);

        player.sendMessage(plugin.getLanguageManager().getMessage("ban_added", "%player%", targetName));
        ejectIfInside(region, target.getUniqueId());
    }

    private void ejectIfInside(Region region, UUID bannedId) {
        Player online = Bukkit.getPlayer(bannedId);
        if (online == null || !region.contains(online.getLocation())) return;

        Location outside = findExit(region, online.getLocation());
        if (outside == null) return;

        online.sendMessage(plugin.getLanguageManager().getMessage("ban_ejected", "%owner%", region.getOwnerName()));
        plugin.getSchedulers().runAtEntity(online, () -> online.teleportAsync(outside));
    }

    private Location findExit(Region region, Location from) {
        if (from.getWorld() == null) return null;

        int y = from.getBlockY();
        int[][] candidates = {
                {region.getBounds().minX() - 2, from.getBlockZ()},
                {region.getBounds().maxX() + 2, from.getBlockZ()},
                {from.getBlockX(), region.getBounds().minZ() - 2},
                {from.getBlockX(), region.getBounds().maxZ() + 2}
        };

        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int[] candidate : candidates) {
            Location option = new Location(from.getWorld(), candidate[0] + 0.5, y, candidate[1] + 0.5,
                    from.getYaw(), from.getPitch());
            double distance = option.distanceSquared(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = option;
            }
        }
        return best;
    }

    private void unban(Player player, Region region, String[] args) {
        if (args.length == 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage("unban_usage"));
            return;
        }

        UUID targetId = findBannedByName(region, args[0]);
        if (targetId == null || !region.isBanned(targetId)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("ban_not_banned", "%player%", args[0]));
            return;
        }

        if (RegionEvents.fireMemberChange(region, player, targetId, args[0],
                RegionMemberChangeEvent.Action.UNBAN, null)) return;
        region.unban(targetId);

        plugin.getRegionStorage().save(region);
        player.sendMessage(plugin.getLanguageManager().getMessage("ban_removed", "%player%", args[0]));
    }

    private void list(Player player, Region region) {
        player.sendMessage(plugin.getLanguageManager().getMessage("banlist_header"));

        if (region.getBannedPlayers().isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("banlist_empty"));
            return;
        }

        for (Map.Entry<UUID, String> entry : region.getBannedPlayers().entrySet()) {
            String name = entry.getValue().isBlank() ? entry.getKey().toString().substring(0, 8) : entry.getValue();
            player.sendMessage(plugin.getLanguageManager().getMessage("banlist_line", "%player%", name));
        }
    }

    private UUID findBannedByName(Region region, String name) {
        for (Map.Entry<UUID, String> entry : region.getBannedPlayers().entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) return entry.getKey();
        }

        OfflinePlayer target = resolvePlayer(name);
        return target == null ? null : target.getUniqueId();
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && cached.getUniqueId() != null ? cached : null;
    }

    @Override
    public List<String> complete(CommandSender sender, Player player, String[] args) {
        if (args.length != 1 || player == null) return List.of();

        if (mode == Mode.BAN) return onlinePlayerNames(args[0]);
        if (mode == Mode.UNBAN) {
            Region region = plugin.getRegionManager().getRegionAt(player.getLocation());
            if (region == null) return List.of();

            return filter(new ArrayList<>(region.getBannedPlayers().values()), args[0]);
        }
        return List.of();
    }
}
