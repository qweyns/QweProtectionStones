package org.qweyns.qweprotectstones.commands.admin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.event.RegionDeleteEvent;
import org.qweyns.qweprotectstones.regions.event.RegionEvents;

import java.util.Locale;
import java.util.UUID;

/** Админ-команды над конкретным приватом: инфо, снос, правки, телепорт, флаги, передача. */
public class RegionAdminCommands {

    private final QweProtectStones plugin;
    private final AdminSupport support;

    public RegionAdminCommands(QweProtectStones plugin, AdminSupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    public void info(CommandSender sender, Player player, String[] args) {
        Region region = support.resolveRegion(player, args);
        if (region == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("region_not_found",
                    "%id%", args.length > 1 ? args[1] : "-"));
            return;
        }

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_info",
                "%id%", region.getId().toString(),
                "%owner%", region.getOwnerName(),
                "%type%", region.getTypeId(),
                "%world%", region.getWorldName(),
                "%x%", String.valueOf(region.getCoreX()),
                "%y%", String.valueOf(region.getCoreY()),
                "%z%", String.valueOf(region.getCoreZ()),
                "%members%", String.valueOf(region.getMemberCount()),
                "%durability%", region.getDurability() + "/" + region.getMaxDurability()));

        String lastAttack = region.getLastAttackAt() == 0
                ? plugin.getLanguageManager().rawTemplate("admin_never")
                : new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date(region.getLastAttackAt()));

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_info_attacks",
                "%count%", String.valueOf(region.getAttackCount()),
                "%last%", lastAttack,
                "%by%", region.getLastAttackerName().isBlank()
                        ? plugin.getLanguageManager().rawTemplate("unknown_owner")
                        : region.getLastAttackerName(),
                "%siege%", plugin.getLanguageManager()
                        .rawTemplate(plugin.isUnderSiege(region) ? "siege_active" : "siege_calm")));
    }

    public void delete(CommandSender sender, Player player, String[] args) {
        Region region = support.resolveRegion(player, args);
        if (region == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("region_not_found",
                    "%id%", args.length > 1 ? args[1] : "-"));
            return;
        }

        if (!plugin.getRegionManager().deleteRegion(region, RegionDeleteEvent.Reason.ADMIN, player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("delete_cancelled"));
            return;
        }

        plugin.getRegionLifecycleListener().cleanupVisuals(region);
        support.auditLog(region, sender, "delete", "owner=" + region.getOwnerName());
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_deleted",
                "%id%", region.getShortId(), "%owner%", region.getOwnerName()));
    }

    public void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_give_usage"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_give_player_offline", "%player%", args[1]));
            return;
        }

        RegionType type = plugin.getRegionTypes().byId(args[2].toLowerCase(Locale.ROOT));
        if (type == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_give_type_unknown", "%type%", args[2]));
            return;
        }

        int amount = 1;
        if (args.length > 3) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_bad_number", "%value%", args[3]));
                return;
            }
        }
        int max = plugin.getConfigManager().getAdminGiveMaxAmount();
        if (amount < 1 || amount > max) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_give_max_amount",
                    "%max%", String.valueOf(max)));
            return;
        }

        // не влезло — падает на землю

        boolean tags = plugin.getConfigManager().getConfig().getBoolean("settings.core-item-tags", true);
        ItemStack core = org.qweyns.qweprotectstones.utils.RegionItems.core(
                plugin, type, amount, null, tags || type.restrictObtaining(), true);
        target.getInventory().addItem(core)
                .forEach((slot, leftover) -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));

        plugin.getCriticalFileLogger().log("ADMIN_GIVE",
                "by=" + sender.getName() + " player=" + target.getName()
                        + " type=" + type.id() + " amount=" + amount);
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_give_sent",
                "%player%", target.getName(), "%type%", type.id(), "%amount%", String.valueOf(amount)));
        target.sendMessage(plugin.getLanguageManager().getMessage("admin_give_received",
                "%type%", type.displayName(), "%amount%", String.valueOf(amount)));
    }

    public void setDurability(CommandSender sender, Player player, String[] args) {
        int value = support.parseInt(sender, args, 2);
        if (value < 0) return;

        Region region = support.resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        region.setDurability(Math.min(value, region.getMaxDurability()));
        plugin.getRegionStorage().save(region);
        support.auditLog(region, sender, "setdurability", "value=" + region.getDurability());
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_setdurability_done",
                "%id%", region.getShortId(), "%value%", region.getDurability() + "/" + region.getMaxDurability()));
    }

    public void setMaxDurability(CommandSender sender, Player player, String[] args) {
        int value = support.parseInt(sender, args, 2);
        if (value < 1) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_bad_number",
                    "%value%", args.length > 2 ? args[2] : "-"));
            return;
        }

        Region region = support.resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        region.setMaxDurability(value);
        plugin.getRegionStorage().save(region);
        support.auditLog(region, sender, "setmax", "value=" + value);
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_setmax_done",
                "%id%", region.getShortId(), "%value%", region.getDurability() + "/" + region.getMaxDurability()));
    }

    public void setType(CommandSender sender, Player player, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_settype_usage"));
            return;
        }

        Region region = support.resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        RegionType type = plugin.getRegionTypes().byId(args[2].toLowerCase(Locale.ROOT));
        if (type == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_give_type_unknown", "%type%", args[2]));
            return;
        }

        Region blocking = plugin.getRegionManager().reapplyTypeBounds(region, type);
        if (blocking != null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_bounds_overlap",
                    "%owner%", blocking.getOwnerName(), "%id%", blocking.getShortId()));
            return;
        }

        region.setTypeId(type.id());
        region.setMaxDurability(type.maxDurability());
        region.setDurability(Math.min(region.getDurability(), type.maxDurability()));

        org.bukkit.World world = region.getWorld();
        if (world != null) {
            world.getBlockAt(region.getCoreX(), region.getCoreY(), region.getCoreZ()).setType(type.material());
        }

        plugin.getRegionStorage().save(region);
        plugin.getHologramManager().createOrUpdateHologram(region);
        if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().update(region);
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().update(region);
        plugin.getVisualManager().showBoundary(region, "create");
        support.auditLog(region, sender, "settype", "type=" + type.id());

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_settype_done",
                "%id%", region.getShortId(), "%type%", type.id(),
                "%size%", type.widthX() + "x" + type.widthZ()));
    }

    public void setBounds(CommandSender sender, Player player, String[] args) {
        if (args.length < 8) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_setbounds_usage"));
            return;
        }

        int[] v = new int[6];
        for (int i = 0; i < 6; i++) {
            try {
                v[i] = Integer.parseInt(args[2 + i]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin_bad_number", "%value%", args[2 + i]));
                return;
            }
        }

        Region region = support.resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        if (v[0] > v[3] || v[1] > v[4] || v[2] > v[5]) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_setbounds_inverted"));
            return;
        }

        Region blocking = plugin.getRegionManager().updateBounds(region, new RegionBounds(v[0], v[1], v[2], v[3], v[4], v[5]));
        if (blocking != null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_bounds_overlap",
                    "%owner%", blocking.getOwnerName(), "%id%", blocking.getShortId()));
            return;
        }

        plugin.getVisualManager().showBoundary(region, "create");
        if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().update(region);
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().update(region);
        support.auditLog(region, sender, "setbounds", "size=" + region.getBounds().sizeX() + "x" + region.getBounds().sizeZ());
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_setbounds_done",
                "%id%", region.getShortId(), "%size%", region.getBounds().sizeX() + "x" + region.getBounds().sizeZ()));
    }

    public void teleport(CommandSender sender, Player player, String[] args) {
        if (player == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("players_only"));
            return;
        }

        Region region = support.resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        org.bukkit.World world = region.getWorld();
        if (world == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_tp_world_unloaded", "%world%", region.getWorldName()));
            return;
        }

        Location target = new Location(world,
                region.getCoreX() + 0.5,
                region.getCoreY() + plugin.getConfigManager().getAdminTeleportOffsetY(),
                region.getCoreZ() + 0.5);
        player.teleportAsync(target);
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_tp_done",
                "%id%", region.getShortId(), "%owner%", region.getOwnerName()));
    }

    public void flag(CommandSender sender, Player player, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_flag_usage"));
            return;
        }

        Region region = support.resolveRegionWithMessage(sender, player, args);
        if (region == null) return;

        var parsedFlag = RegionFlag.parse(args[2]);
        if (parsedFlag.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("flag_unknown", "%flag%", args[2]));
            return;
        }
        RegionFlag flag = parsedFlag.get();

        String raw = args[3].toLowerCase(Locale.ROOT);
        if (raw.equals("reset") || raw.equals("default") || raw.equals("сброс")) {
            if (RegionEvents.fireFlagChange(region, player, flag, null)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("flag_change_cancelled"));
                return;
            }
            region.resetFlag(flag);
        } else {
            Boolean value = support.parseBoolean(raw);
            if (value == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("flag_bad_value", "%value%", args[3]));
                return;
            }
            if (RegionEvents.fireFlagChange(region, player, flag, value)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("flag_change_cancelled"));
                return;
            }
            region.setFlag(flag, value);
        }

        plugin.getRegionStorage().save(region);
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin_flag_done",
                "%id%", region.getShortId(), "%flag%", flag.key(), "%value%", args[3]));
    }

    public void transfer(CommandSender sender, Player player, String[] args, boolean forgetPrevious) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getMessage(
                    forgetPrevious ? "admin_setowner_usage" : "admin_transfer_usage"));
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
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_transfer_self", "%player%", target.getName()));
            return;
        }

        if (RegionEvents.fireTransfer(region, player, target.getUniqueId(), target.getName())) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin_action_cancelled"));
            return;
        }

        UUID previousOwner = region.getOwnerId();
        plugin.getRegionManager().transferRegion(region, target.getUniqueId(), target.getName());
        if (forgetPrevious && previousOwner != null) {
            region.removeMember(previousOwner);
            plugin.getRegionStorage().save(region);
        }
        if (plugin.getDynmapIntegration() != null) plugin.getDynmapIntegration().update(region);
        if (plugin.getBlueMapIntegration() != null) plugin.getBlueMapIntegration().update(region);

        support.auditLog(region, sender, forgetPrevious ? "setowner" : "transfer", "to=" + target.getName());
        sender.sendMessage(plugin.getLanguageManager().getMessage(
                forgetPrevious ? "admin_setowner_done" : "admin_transfer_done",
                "%id%", region.getShortId(),
                "%old%", previousOwner == null ? "-" : region.getOwnerName(),
                "%new%", target.getName()));
    }
}
