package org.qweyns.qweprotectstones.menus;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.util.Locale;

public class MenuRequirements {

    private final java.util.Set<String> warnedUnknown = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final QweProtectStones plugin;
    private final MenuPlaceholders placeholders;

    public MenuRequirements(QweProtectStones plugin, MenuPlaceholders placeholders) {
        this.plugin = plugin;
        this.placeholders = placeholders;
    }

    public boolean passes(ConfigurationSection itemCfg, String path, Player player, Region region) {
        if (!itemCfg.contains(path)) return true;

        ConfigurationSection requirements = itemCfg.getConfigurationSection(path);
        return requirements == null || check(requirements, player, region);
    }

    private boolean check(ConfigurationSection reqs, Player player, Region region) {
        for (String key : reqs.getKeys(false)) {
            String type = reqs.getString(key + ".type");
            if (type == null) continue;

            if (!evaluate(type.toLowerCase(Locale.ROOT), reqs, key, player, region)) return false;
        }
        return true;
    }

    private boolean evaluate(String type, ConfigurationSection reqs, String key, Player player, Region region) {
        return switch (type) {
            case "has money" -> plugin.getVaultHook().hasMoney(player, reqs.getDouble(key + ".amount"));
            case "has points" -> plugin.getPlayerPointsHook().hasPoints(player, reqs.getInt(key + ".amount"));
            case "has exp" -> player.getLevel() >= reqs.getInt(key + ".amount");
            case "has permission" -> {
                String permission = reqs.getString(key + ".permission");
                yield permission == null || player.hasPermission(permission);
            }
            case "region_durability_enabled" -> region != null && durabilityEnabled(region);
            case "region_durability_disabled" -> region != null && !durabilityEnabled(region);
            case "has effect" -> region != null && placeholders.effectLevel(region, reqs.getString(key + ".effect_name")) > 0;
            case "does not have effect" -> region != null && placeholders.effectLevel(region, reqs.getString(key + ".effect_name")) <= 0;
            case "allowed_effect" -> region != null && isEffectAllowed(region, reqs.getString(key + ".effect_name"));
            case "not_allowed_effect" -> region != null && !isEffectAllowed(region, reqs.getString(key + ".effect_name"));
            case "is_owner" -> region != null && region.isOwner(player.getUniqueId());
            case "under_siege" -> region != null && plugin.isUnderSiege(region);
            case "trust_level" -> {
                var required = TrustLevel.parse(reqs.getString(key + ".level"));
                yield region != null && required.isPresent()
                        && plugin.getProtectionService().has(region, player, required.get());
            }
            // опечатка в типе не должна открывать платные кнопки всем
            default -> {
                if (warnedUnknown.add(type)) {
                    plugin.getLogger().warning("Неизвестный тип требования в меню: '" + type + "' — требование не пройдено.");
                }
                yield false;
            }
        };
    }

    private boolean durabilityEnabled(Region region) {
        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        return type != null && type.durabilityUpgradeEnabled();
    }

    public boolean isEffectAllowed(Region region, String effectName) {
        if (effectName == null) return false;

        RegionType type = plugin.getRegionTypes().byId(region.getTypeId());
        return type != null && type.isEffectAllowed(effectName);
    }
}
