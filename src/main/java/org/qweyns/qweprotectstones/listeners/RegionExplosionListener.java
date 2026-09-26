package org.qweyns.qweprotectstones.listeners;

import org.qweyns.qweprotectstones.config.ConfigValues;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.api.SiegeService;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionBounds;
import org.qweyns.qweprotectstones.regions.RegionFlag;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.regions.event.RegionExplosionTypeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/** Защита списка блоков отдельно от побочных эффектов принятого взрыва. */
public class RegionExplosionListener implements Listener {
    public static final double MAX_DAMAGE_RADIUS = 256.0;
    private final QweProtectStones plugin;
    private final SiegeService siege;

    // Всё, что раньше считалось на каждый взрыв, — один раз при старте и /reload.
    private volatile Settings settings;

    private record Settings(double maxBaseRadius, int damage, Map<String, String> entityTypes,
                            List<Map.Entry<String, String>> blockTypes, String defaultEntity, String defaultBlock) { }

    private final org.qweyns.qweprotectstones.diagnostics.PerfStats perf;
    private final org.qweyns.qweprotectstones.diagnostics.PerfStats.Timer perf_onEntityExplodeApplied;

    public RegionExplosionListener(QweProtectStones plugin, SiegeService siege) {
        this.perf = plugin.getPerfStats() != null ? plugin.getPerfStats() : new org.qweyns.qweprotectstones.diagnostics.PerfStats();
        this.perf_onEntityExplodeApplied = perf.timer("explosion-siege");
        this.plugin = plugin;
        this.siege = siege;
    }

    /** Пересчитать кэш после перезагрузки конфигов и типов приватов. */
    public void reload() { settings = null; }

    private Settings settings() {
        Settings current = settings;
        if (current != null) return current;
        var cfg = plugin.getConfigManager().getConfig();
        double maximum = baseRadius(null);
        for (RegionType type : plugin.getRegionTypes().all()) maximum = Math.max(maximum, baseRadius(type));
        int damage = (int) ConfigValues.boundedLong(cfg, "siege.damage_per_explosion", 1L, 1L, 1000000L);

        Map<String, String> entities = new HashMap<>();
        ConfigurationSection entitySection = cfg.getConfigurationSection("siege.explosion_types.entities");
        if (entitySection != null) for (String key : entitySection.getKeys(false)) {
            String value = entitySection.getString(key);
            if (value != null && !value.isBlank()) entities.put(key.toUpperCase(Locale.ROOT), value.trim().toUpperCase(Locale.ROOT));
        }
        List<Map.Entry<String, String>> blocks = new ArrayList<>();
        ConfigurationSection blockSection = cfg.getConfigurationSection("siege.explosion_types.blocks");
        if (blockSection != null) for (String key : blockSection.getKeys(false)) {
            String value = blockSection.getString(key);
            if (value != null && !value.isBlank()) blocks.add(Map.entry(key.toUpperCase(Locale.ROOT), value.trim().toUpperCase(Locale.ROOT)));
        }
        current = new Settings(maximum, damage, Map.copyOf(entities), List.copyOf(blocks),
                cfg.getString("siege.explosion_types.default_entity", "OTHER").trim().toUpperCase(Locale.ROOT),
                cfg.getString("siege.explosion_types.default_block", "OTHER").trim().toUpperCase(Locale.ROOT));
        settings = current;
        return current;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) { protect(event.blockList(), false); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) { protect(event.blockList(), causedByMob(event.getEntity())); }

    /**
     * Взрыв устроил моб: крипер, визер, дракон или снаряд моба (огненный шар гаста, череп визера).
     * Такие взрывы ломают блоки, только если у привата включены И explosion_damage, И mob_griefing —
     * иначе на анархии с explosion_damage криперы разносили бы базы без всякого рейда.
     */
    static boolean causedByMob(Entity entity) {
        if (entity instanceof org.bukkit.entity.Mob || entity instanceof org.bukkit.entity.EnderDragon) return true;
        if (entity instanceof org.bukkit.entity.Projectile projectile) {
            var shooter = projectile.getShooter();
            return shooter instanceof org.bukkit.entity.LivingEntity && !(shooter instanceof Player);
        }
        return false;
    }

    private void protect(List<Block> blocks, boolean mobCaused) {
        if (blocks.isEmpty()) return;
        String world = blocks.get(0).getWorld().getName();
        var manager = plugin.getRegionManager();
        var protection = plugin.getProtectionService();
        // соседние блоки взрыва почти всегда в одном привате — проверяем его первым
        Region[] last = new Region[1];
        Boolean[] lastAllows = new Boolean[1];
        blocks.removeIf(block -> {
            int x = block.getX(), y = block.getY(), z = block.getZ();
            Region region = last[0] != null && last[0].getBounds().contains(x, y, z) ? last[0]
                    : manager.getRegionAt(block.getWorld(), x, y, z);
            if (region == null) return false;
            if (region.isCore(x, y, z)) return true;
            if (region != last[0]) {
                last[0] = region;
                lastAllows[0] = protection.flag(region, RegionFlag.EXPLOSION_DAMAGE)
                        && (!mobCaused || protection.flag(region, RegionFlag.MOB_GRIEFING));
            }
            return !lastAllows[0];
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplodeApplied(BlockExplodeEvent event) {
        damage(event.getBlock().getLocation(), null, event.getBlock(), classifyBlock(event), null);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplodeApplied(EntityExplodeEvent event) {
        long started = perf.start();
        try {
            onEntityExplodeAppliedTimed(event);
        } finally {
            perf.stop(perf_onEntityExplodeApplied, started);
        }
    }

    private void onEntityExplodeAppliedTimed(EntityExplodeEvent event) {
        // TNT в воде или лаве по ванили не ломает блоки — по умолчанию не снимает и прочность ядра
        if (!plugin.getConfigManager().getConfig().getBoolean("siege.liquid_explosions_damage", false)
                && event.getLocation().getBlock().isLiquid()) return;
        String primer = event.getEntity() instanceof TNTPrimed primed
                && primed.getSource() instanceof Player player ? player.getName() : null;
        damage(event.getLocation(), event.getEntity(), null, classify(event.getEntityType()), primer);
    }

    /** Тип взрыва сущности — siege.explosion_types.entities в siege.yml. */
    private String classify(EntityType type) {
        Settings current = settings();
        return current.entityTypes().getOrDefault(type.name(), current.defaultEntity());
    }

    /** Тип взрыва блока (кровать, якорь возрождения) — siege.explosion_types.blocks; в Paper блок уже заменён воздухом. */
    private String classifyBlock(BlockExplodeEvent event) {
        Settings current = settings();
        String material;
        try {
            material = event.getExplodedBlockState().getType().name();
        } catch (Throwable ignored) {
            material = event.getBlock().getType().name();
        }
        for (Map.Entry<String, String> rule : current.blockTypes()) {
            String pattern = rule.getKey();
            boolean matches = pattern.equals(material)
                    || (pattern.startsWith("*") && material.endsWith(pattern.substring(1)))
                    || (pattern.endsWith("*") && material.startsWith(pattern.substring(0, pattern.length() - 1)));
            if (matches) return rule.getValue();
        }
        return current.defaultBlock();
    }

    private double baseRadius(RegionType type) {
        double value = type != null && type.overridesExplosionRadius() ? type.explosionDamageRadius()
                : plugin.getConfigManager().getExplosionDamageRadius();
        return Double.isFinite(value) ? Math.max(0, Math.min(MAX_DAMAGE_RADIUS, value)) : 0;
    }

    private void damage(Location center, Entity source, Block block, String defaultType, String primer) {
        if (center.getWorld() == null || !plugin.getConfigManager().isSiegeEnabled()) return;
        Settings current = settings();
        // Классификация нужна и вне границ привата: именно аддон расширяет область поиска.
        RegionExplosionTypeEvent event = new RegionExplosionTypeEvent(source, block, defaultType);
        Bukkit.getPluginManager().callEvent(event);
        int radius = (int) Math.ceil(Math.min(MAX_DAMAGE_RADIUS, current.maxBaseRadius() * event.getDamageRadiusMultiplier()));
        RegionBounds bounds = RegionBounds.around(center.getBlockX(), center.getBlockY(), center.getBlockZ(),
                radius, radius, radius, center.getWorld().getMinHeight(), center.getWorld().getMaxHeight() - 1);
        List<Region> candidates = plugin.getRegionManager().intersecting(center.getWorld().getName(), bounds);
        if (candidates.isEmpty()) return;
        for (Region region : candidates) {
            if (!siege.isDamaging(region, event.getExplosionType())) continue;
            Location core = region.getCoreLocation();
            double allowed = Math.min(MAX_DAMAGE_RADIUS,
                    baseRadius(plugin.getRegionTypes().byId(region.getTypeId())) * event.getDamageRadiusMultiplier());
            if (core == null || core.distanceSquared(center) > allowed * allowed) continue;
            if (plugin.getSchedulers().ownsLocation(core)) siege.damageRegion(region, current.damage(), event.getExplosionType(), primer);
            else siege.damageRegionAsync(region, current.damage(), event.getExplosionType(), primer).exceptionally(error -> {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Ошибка межрегиональной осады", error);
                return false;
            });
        }
    }
}
