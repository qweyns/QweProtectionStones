package org.qweyns.qweprotectstones.regions;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Тип привата: блок-ядро и всё, что от него зависит — размер территории,
 * прочность, флаги по умолчанию, лимиты и права на установку.
 *
 * <p>Собирается один раз при загрузке конфига, дальше только читается,
 * поэтому все коллекции неизменяемые.</p>
 */
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

        // Ниже — переопределения глобальных настроек. Отрицательное значение
        // или пустая строка означают «взять общее значение из config.yml».
        int damageCooldownTicks,
        int explosionDamageRadius,
        boolean raidImmune,
        String upgradeItem,
        int upgradeCostMultiplier,
        int upgradeTax,
        String borderColor,
        String menuName,
        String greeting,
        String farewell,
        boolean fullHeight,

        // Ограничено ли получение: true — приват создаёт только предмет,
        // выданный командой /qps give или скрафченный по recipe.
        boolean restrictObtaining,
        // Внешний вид предмета-ядра, когда его создаёт плагин.
        String itemName,
        List<String> itemLore,
        boolean itemGlow,
        // Собственный крафт; null — блок получается как обычный.
        CoreRecipe recipe
) {

    public RegionType {
        upgradeItem = upgradeItem == null ? "" : upgradeItem.trim();
        borderColor = borderColor == null ? "" : borderColor.trim();
        menuName = menuName == null ? "" : menuName.trim();
        greeting = greeting == null ? "" : greeting.trim();
        farewell = farewell == null ? "" : farewell.trim();

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

    /** Значение флага для этого типа: переопределение из конфига или значение по умолчанию. */
    public boolean flagDefault(RegionFlag flag) {
        Boolean configured = defaultFlags.get(flag);
        return configured != null ? configured : flag.defaultValue();
    }

    /** Наносит ли этот вид взрыва урон ядру. */
    public boolean explosionDamages(String explosionType) {
        return explosionRules.getOrDefault(explosionType, Boolean.FALSE);
    }

    public boolean isWorldAllowed(String worldName) {
        if (allowedWorlds.isEmpty()) return true;
        boolean listed = allowedWorlds.contains(worldName.toLowerCase(java.util.Locale.ROOT));
        return allowedWorldsIsBlacklist != listed;
    }

    public boolean isEffectAllowed(String effectName) {
        return allowedEffects.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(effectName));
    }

    /** Есть ли у типа своё значение вместо глобального. */
    public boolean overridesDamageCooldown() { return damageCooldownTicks >= 0; }

    public boolean overridesExplosionRadius() { return explosionDamageRadius >= 0; }

    public boolean overridesUpgradeItem() { return !upgradeItem.isBlank(); }

    public boolean overridesUpgradeMultiplier() { return upgradeCostMultiplier > 0; }

    public boolean overridesUpgradeTax() { return upgradeTax >= 0; }

    public boolean hasBorderColor() { return !borderColor.isBlank(); }

    /** Меню, которое открывает клик по ядру; пусто — выбрать по наличию прокачки. */
    public boolean hasMenu() { return !menuName.isBlank(); }

    /** Размер стороны территории в блоках — для сообщений игроку. */
    public int widthX() { return radiusX * 2 + 1; }

    public int widthZ() { return radiusZ * 2 + 1; }
}
