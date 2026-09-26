package org.qweyns.qweprotectstones.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.qweyns.qweprotectstones.config.Tunables.TrustAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Какое действие роли нужно для взаимодействия с блоком, предметом или сущностью.
 * Всё берётся из protection.yml (protection.interact); порядок правил — как в конфиге.
 */
public final class InteractRules {

    private record Rule<T>(TrustAction action, Set<T> values) { }

    private final List<Rule<Material>> items;
    private final List<Rule<Material>> blocks;
    private final List<Rule<Material>> physical;
    private final List<Rule<EntityType>> entities;
    private final TrustAction defaultBlock;
    private final TrustAction defaultPhysical;
    private final TrustAction defaultEntity;
    private final TrustAction containers;
    private final boolean keepItemUse;

    private InteractRules(List<Rule<Material>> items, List<Rule<Material>> blocks, List<Rule<Material>> physical,
                          List<Rule<EntityType>> entities, TrustAction defaultBlock, TrustAction defaultPhysical,
                          TrustAction defaultEntity, TrustAction containers, boolean keepItemUse) {
        this.items = items; this.blocks = blocks; this.physical = physical; this.entities = entities;
        this.defaultBlock = defaultBlock; this.defaultPhysical = defaultPhysical; this.defaultEntity = defaultEntity;
        this.containers = containers; this.keepItemUse = keepItemUse;
    }

    /** Действие по предмету в руке при клике по блоку; null — предмет правилами не описан. */
    public TrustAction forItem(Material item) {
        if (item == null || item.isAir()) return null;
        return first(items, item);
    }

    /** Действие по блоку; null — ни одно правило не подошло (тогда контейнер или default). */
    public TrustAction forBlock(Material block) { return first(blocks, block); }

    public TrustAction forPhysical(Material block) {
        TrustAction action = first(physical, block);
        return action != null ? action : defaultPhysical;
    }

    public TrustAction forEntity(EntityType type) {
        TrustAction action = first(entities, type);
        return action != null ? action : defaultEntity;
    }

    public TrustAction containers() { return containers; }
    public TrustAction defaultBlock() { return defaultBlock; }

    /** При запрете клика по блоку не отменять использование предмета (еда, лук, жемчуг). */
    public boolean keepItemUse() { return keepItemUse; }

    private static <T> TrustAction first(List<Rule<T>> rules, T value) {
        for (Rule<T> rule : rules) if (rule.values().contains(value)) return rule.action();
        return null;
    }

    public static InteractRules load(FileConfiguration cfg, Logger logger) {
        String base = "protection.interact";
        return new InteractRules(
                materialRules(cfg.getConfigurationSection(base + ".items"), base + ".items", logger),
                materialRules(cfg.getConfigurationSection(base + ".blocks"), base + ".blocks", logger),
                materialRules(cfg.getConfigurationSection(base + ".physical"), base + ".physical", logger),
                entityRules(cfg.getConfigurationSection(base + ".entities"), base + ".entities", logger),
                action(cfg, base + ".default-block-action", TrustAction.CONTAINER, logger),
                action(cfg, base + ".default-physical-action", TrustAction.INTERACT, logger),
                action(cfg, base + ".default-entity-action", TrustAction.CONTAINER, logger),
                action(cfg, base + ".container-action", TrustAction.CONTAINER, logger),
                cfg.getBoolean(base + ".keep-item-use", true));
    }

    private static TrustAction action(FileConfiguration cfg, String path, TrustAction fallback, Logger logger) {
        String raw = cfg.getString(path);
        if (raw == null || raw.isBlank()) return fallback;
        return TrustAction.parse(raw).orElseGet(() -> {
            logger.warning(path + ": неизвестное действие '" + raw + "', использую " + fallback.key());
            return fallback;
        });
    }

    private static List<Rule<Material>> materialRules(ConfigurationSection section, String path, Logger logger) {
        List<Rule<Material>> result = new ArrayList<>();
        if (section == null) return result;
        for (Map.Entry<TrustAction, List<String>> entry : entries(section, path, logger)) {
            result.add(new Rule<>(entry.getKey(), MaterialPatterns.materials(entry.getValue(),
                    bad -> logger.warning(path + "." + entry.getKey().key() + ": неизвестный материал или тег '" + bad + "'"))));
        }
        return List.copyOf(result);
    }

    private static List<Rule<EntityType>> entityRules(ConfigurationSection section, String path, Logger logger) {
        List<Rule<EntityType>> result = new ArrayList<>();
        if (section == null) return result;
        for (Map.Entry<TrustAction, List<String>> entry : entries(section, path, logger)) {
            result.add(new Rule<>(entry.getKey(), MaterialPatterns.entities(entry.getValue(),
                    bad -> logger.warning(path + "." + entry.getKey().key() + ": неизвестная сущность '" + bad + "'"))));
        }
        return List.copyOf(result);
    }

    private static List<Map.Entry<TrustAction, List<String>>> entries(ConfigurationSection section, String path, Logger logger) {
        List<Map.Entry<TrustAction, List<String>>> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            var action = TrustAction.parse(key);
            if (action.isEmpty()) {
                logger.warning(path + "." + key + ": неизвестное действие (см. список в roles.yml)");
                continue;
            }
            result.add(Map.entry(action.get(), section.getStringList(key)));
        }
        return result;
    }
}
