package org.qweyns.qweprotectstones.regions;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record RegionType(
        String id,
        Material material,
        String displayName,
        int radiusX,
        int radiusY,
        int radiusZ,
        int startDurability,
        int maxDurability,
        boolean durabilityUpgradeEnabled,
        int maxPerPlayer,
        int minDistanceToOthers,
        String placePermission,
        Set<String> allowedWorlds,
        boolean allowedWorldsIsBlacklist,
        List<String> allowedEffects,
        Map<RegionFlag, Boolean> defaultFlags,
        Map<String, Boolean> explosionRules,
        boolean spawnEggAllowed,
        boolean hologramEnabled,
        boolean returnBlockOnRemove,

        boolean sneakPlacesPlainBlock,

        int damageCooldownTicks,
        int explosionDamageRadius,
        boolean raidImmune,
        String upgradeItem,
        int upgradeCostMultiplier,
        int upgradeTax,
        String borderColor,
        String menuName,
        boolean fullHeight,

        boolean restrictObtaining,

        String itemName,
        List<String> itemLore,
        boolean itemGlow,

        CoreRecipe recipe
) {

    public RegionType {
        upgradeItem = upgradeItem == null ? "" : upgradeItem.trim();
        borderColor = borderColor == null ? "" : borderColor.trim();
        menuName = menuName == null ? "" : menuName.trim();

        allowedWorlds = Set.copyOf(allowedWorlds);
        allowedEffects = List.copyOf(allowedEffects);
        defaultFlags = Map.copyOf(defaultFlags);
        explosionRules = Map.copyOf(explosionRules);
        itemName = itemName == null ? "" : itemName.trim();
        itemLore = itemLore == null ? List.of() : List.copyOf(itemLore);
        radiusX = Math.max(0, radiusX);
        radiusY = Math.max(0, radiusY);
        radiusZ = Math.max(0, radiusZ);
        startDurability = Math.max(1, startDurability);
        maxDurability = Math.max(startDurability, maxDurability);
    }

    public boolean flagDefault(RegionFlag flag) {
        Boolean configured = defaultFlags.get(flag);
        return configured != null ? configured : flag.defaultValue();
    }

    public boolean explosionDamages(String explosionType) {
        if (explosionType == null) return false;
        return explosionRules.getOrDefault(explosionType.toUpperCase(java.util.Locale.ROOT), Boolean.FALSE);
    }

    public boolean isWorldAllowed(String worldName) {
        if (allowedWorlds.isEmpty()) return true;
        boolean listed = allowedWorlds.contains(worldName.toLowerCase(java.util.Locale.ROOT));
        return allowedWorldsIsBlacklist != listed;
    }

    public boolean isEffectAllowed(String effectName) {
        return allowedEffects.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(effectName));
    }

    public boolean overridesDamageCooldown() { return damageCooldownTicks >= 0; }

    public boolean overridesExplosionRadius() { return explosionDamageRadius >= 0; }

    public boolean overridesUpgradeItem() { return !upgradeItem.isBlank(); }

    public boolean overridesUpgradeMultiplier() { return upgradeCostMultiplier > 0; }

    public boolean overridesUpgradeTax() { return upgradeTax >= 0; }

    public boolean hasBorderColor() { return !borderColor.isBlank(); }

    public boolean hasMenu() { return !menuName.isBlank(); }

    public int widthX() { return radiusX * 2 + 1; }

    public int widthZ() { return radiusZ * 2 + 1; }
}
