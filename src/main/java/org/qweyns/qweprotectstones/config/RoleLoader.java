package org.qweyns.qweprotectstones.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.qweyns.qweprotectstones.config.Tunables.TrustAction;
import org.qweyns.qweprotectstones.regions.TrustLevel;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Чтение ролей из секции {@code roles} (файл roles.yml). */
public final class RoleLoader {

    private RoleLoader() { }

    public static TrustLevel.Registry load(FileConfiguration cfg, Logger logger) {
        if (cfg.isSet("trust.required") || cfg.isSet("trust.flag_edit_level") || cfg.isSet("trust.member_edit_level")) {
            logger.warning("Секция trust в config.yml больше не используется: права ролей настраиваются в roles.yml "
                    + "(roles.list.<роль>.actions). Перенесите свои значения и удалите секцию trust.");
        }

        ConfigurationSection root = cfg.getConfigurationSection("roles");
        ConfigurationSection list = root == null ? null : root.getConfigurationSection("list");
        if (list == null || !cfg.contains("roles.list", true) || list.getKeys(false).isEmpty()) {
            logger.warning("roles.list пуст — использую стандартные роли access/container/build/manager.");
            return TrustLevel.Registry.builtin();
        }

        Map<String, ConfigurationSection> raw = new LinkedHashMap<>();
        for (String key : list.getKeys(false)) {
            ConfigurationSection section = list.getConfigurationSection(key);
            String id = TrustLevel.normalize(key);
            if (section == null || id.isEmpty()) continue;
            if (id.equals(TrustLevel.OWNER_ID)) {
                logger.warning("roles.list." + key + ": роль owner встроенная, её название задаётся в roles.owner-display.");
                continue;
            }
            if (!id.matches("[a-z0-9_\\-]{1,16}")) {
                logger.warning("roles.list." + key + ": id роли — латиница, цифры, _ и -, до 16 символов. Роль пропущена.");
                continue;
            }
            raw.put(id, section);
        }

        Map<String, TrustLevel> roles = new LinkedHashMap<>();
        for (String id : raw.keySet()) {
            Set<TrustAction> actions = resolveActions(id, raw, new HashSet<>(), logger);
            ConfigurationSection section = raw.get(id);
            roles.put(id, new TrustLevel(id,
                    own(section, "display") ? section.getString("display", id) : id,
                    own(section, "weight") ? section.getInt("weight", 0) : 0,
                    actions,
                    !own(section, "grantable") || section.getBoolean("grantable", true)));
        }

        Map<String, String> aliases = new LinkedHashMap<>();
        ConfigurationSection aliasSection = root.getConfigurationSection("aliases");
        if (aliasSection != null) {
            for (String from : aliasSection.getKeys(false)) {
                String to = aliasSection.getString(from);
                if (to != null && (roles.containsKey(TrustLevel.normalize(to)) || TrustLevel.OWNER_ID.equals(TrustLevel.normalize(to)))) {
                    aliases.put(from, to);
                } else {
                    logger.warning("roles.aliases." + from + ": роль '" + to + "' не найдена.");
                }
            }
        }

        String defaultRole = root.getString("default-role", "build");
        if (!roles.containsKey(TrustLevel.normalize(defaultRole)) && !aliases.containsKey(TrustLevel.normalize(defaultRole))) {
            logger.warning("roles.default-role: роль '" + defaultRole + "' не найдена, беру самую старшую выдаваемую.");
        }
        String publicRole = root.getString("public-access-role", "container");
        return new TrustLevel.Registry(roles, aliases, defaultRole, publicRole, root.getString("owner-display", null));
    }

    /** Значение задано в файле сервера, а не пришло из дефолтов jar. */
    private static boolean own(ConfigurationSection section, String path) {
        return section.contains(path, true);
    }

    private static Set<TrustAction> resolveActions(String id, Map<String, ConfigurationSection> raw,
                                                   Set<String> visiting, Logger logger) {
        EnumSet<TrustAction> result = EnumSet.noneOf(TrustAction.class);
        ConfigurationSection section = raw.get(id);
        if (section == null || !visiting.add(id)) {
            if (section != null) logger.warning("roles.list." + id + ": циклическое наследование (inherit).");
            return result;
        }

        List<String> parents = !own(section, "inherit") ? List.of()
                : section.isList("inherit") ? section.getStringList("inherit")
                : List.of(String.valueOf(section.getString("inherit", "")));
        for (String parent : parents) {
            String parentId = TrustLevel.normalize(parent);
            if (parentId.isEmpty()) continue;
            if (!raw.containsKey(parentId)) {
                logger.warning("roles.list." + id + ".inherit: роль '" + parent + "' не найдена.");
                continue;
            }
            result.addAll(resolveActions(parentId, raw, visiting, logger));
        }
        List<String> actions = own(section, "actions") ? section.getStringList("actions") : List.of();
        for (String entry : actions) {
            String value = entry.trim().toLowerCase(Locale.ROOT);
            boolean deny = value.startsWith("-");
            if (deny) value = value.substring(1);
            if (value.equals("*")) {
                if (deny) result.clear(); else result.addAll(EnumSet.allOf(TrustAction.class));
                continue;
            }
            var action = TrustAction.parse(value);
            if (action.isEmpty()) {
                logger.warning("roles.list." + id + ".actions: неизвестное действие '" + entry + "'.");
                continue;
            }
            if (deny) result.remove(action.get()); else result.add(action.get());
        }
        visiting.remove(id);
        return result;
    }
}
