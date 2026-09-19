package org.qweyns.qweprotectstones.menus;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionMember;
import org.qweyns.qweprotectstones.regions.TrustLevel;
import org.qweyns.qweprotectstones.regions.event.RegionEvents;
import org.qweyns.qweprotectstones.regions.event.RegionMemberChangeEvent;
import org.qweyns.qweprotectstones.utils.ColorUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MenuMembers {

    private final QweProtectStones plugin;
    private final MenuManager menuManager;

    public MenuMembers(QweProtectStones plugin, MenuManager menuManager) {
        this.plugin = plugin;
        this.menuManager = menuManager;
    }

    void renderSlots(Player player, MenuHolder holder, Region region, FileConfiguration menuCfg, ItemStack[] contents) {
        if (region == null) return;
        List<RegionMember> members = new ArrayList<>(region.getMembers());

        int slot = 0;
        for (RegionMember member : members) {
            if (slot >= contents.length - 9) break;

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(plugin.getServer().getOfflinePlayer(member.uuid()));

                String roleColor = member.trust().color() != null ? member.trust().color() : "<white>";
                String roleName = member.trust().displayName() != null ? member.trust().displayName() : member.trust().name();

                meta.displayName(ColorUtil.formatComponent("<bold><#FDE68A>" + member.name() + "</bold>"));

                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(ColorUtil.formatComponent("<#F2EFFA>▪ Текущая роль: " + roleColor + roleName));
                lore.add(ColorUtil.formatComponent(""));
                lore.add(ColorUtil.formatComponent("<#86EFAC>▶ ЛКМ — Повысить роль"));
                lore.add(ColorUtil.formatComponent("<#FB7185>▶ ПКМ — Понизить / Выгнать"));

                meta.lore(lore);
                skull.setItemMeta(meta);
            }
            contents[slot] = skull;
            slot++;
        }
    }

    boolean handleClick(Player player, MenuHolder holder, FileConfiguration menuCfg, int slot, boolean isRightClick) {
        Region region = holder.region;
        if (region == null) return true;

        if (slot >= holder.baseLayer.length - 9 || holder.baseLayer[slot] == null || holder.baseLayer[slot].getType() != Material.PLAYER_HEAD) {
            return false;
        }

        if (!plugin.getProtectionService().canManage(player, region)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("no_region_access",
                    "%level%", plugin.getLanguageManager().rawTemplate("trust_manager")));
            return true;
        }

        List<RegionMember> members = new ArrayList<>(region.getMembers());
        if (slot >= members.size()) return true;

        RegionMember target = members.get(slot);

        TrustLevel actorTrust = plugin.getProtectionService().trustOf(region, player);
        if (actorTrust == null || target.trust().weight() >= actorTrust.weight()) {
            plugin.getTunables().menuDenied().playTo(player);
            return true;
        }

        TrustLevel[] grantable = TrustLevel.grantable();
        List<TrustLevel> roleList = Arrays.asList(grantable);
        roleList.sort((a, b) -> Integer.compare(a.weight(), b.weight()));

        int currentIndex = roleList.indexOf(target.trust());

        if (isRightClick) {
            if (currentIndex <= 0) {
                if (RegionEvents.fireMemberChange(region, player, target.uuid(), target.name(), RegionMemberChangeEvent.Action.UNTRUST, null)) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("admin_action_cancelled"));
                    return true;
                }
                region.removeMember(target.uuid());
                plugin.getRegionStorage().save(region);
                plugin.getTunables().menuSuccess().playTo(player);
                menuManager.render(player, holder, true);
                return true;
            } else {
                TrustLevel newRole = roleList.get(currentIndex - 1);
                changeRole(region, player, target, newRole, holder);
            }
        } else {
            if (currentIndex >= roleList.size() - 1) {
                plugin.getTunables().menuDenied().playTo(player);
                return true;
            } else {
                TrustLevel newRole = roleList.get(currentIndex + 1);
                if (newRole.weight() >= actorTrust.weight()) {
                    plugin.getTunables().menuDenied().playTo(player);
                    return true;
                }
                changeRole(region, player, target, newRole, holder);
            }
        }

        return true;
    }

    private void changeRole(Region region, Player player, RegionMember target, TrustLevel newRole, MenuHolder holder) {
        if (RegionEvents.fireMemberChange(region, player, target.uuid(), target.name(), RegionMemberChangeEvent.Action.TRUST, newRole)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("admin_action_cancelled"));
            return;
        }
        region.setMember(target.uuid(), target.name(), newRole);
        plugin.getRegionStorage().save(region);
        plugin.getTunables().menuSuccess().playTo(player);
        menuManager.render(player, holder, true);
    }
}
