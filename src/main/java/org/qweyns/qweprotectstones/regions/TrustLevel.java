package org.qweyns.qweprotectstones.regions;

import org.qweyns.qweprotectstones.config.Tunables.TrustAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Роль участника привата. Роли полностью описываются в {@code roles.yml}:
 * идентификатор, отображаемое имя, вес (иерархия) и набор разрешённых действий.
 *
 * <p>Встроенная только одна роль — {@link #OWNER}: у неё все действия и наибольший вес,
 * её название тоже настраивается. Остальные роли (в том числе стандартные
 * access/container/build/manager) — обычные записи конфига, их можно переименовать,
 * удалить или добавить свои.</p>
 *
 * <p>Участник хранит id роли строкой ({@link RegionMember}); если роль удалили из конфига,
 * id сохраняется в базе, а сама роль считается «сиротой» без прав, пока её не вернут
 * или не заменят через {@code roles.aliases}.</p>
 */
public final class TrustLevel {

    public static final String OWNER_ID = "owner";

    /** Владелец привата. Вес и права фиксированы, название — из roles.yml. */
    public static final TrustLevel OWNER = new TrustLevel(OWNER_ID, "Владелец", Integer.MAX_VALUE,
            EnumSet.allOf(TrustAction.class), false, true);

    private static volatile Registry registry = Registry.builtin();

    private final String id;
    private final String displayName;
    private final int weight;
    private final Set<TrustAction> actions;
    private final boolean grantable;
    private final boolean known;

    public TrustLevel(String id, String displayName, int weight, Set<TrustAction> actions, boolean grantable) {
        this(id, displayName, weight, actions, grantable, true);
    }

    private TrustLevel(String id, String displayName, int weight, Set<TrustAction> actions,
                       boolean grantable, boolean known) {
        this.id = normalize(id);
        this.displayName = displayName == null || displayName.isBlank() ? this.id : displayName;
        this.weight = weight;
        this.actions = actions.isEmpty() ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(actions));
        this.grantable = grantable;
        this.known = known;
    }

    // ---------------------------------------------------------------- данные роли

    /** Идентификатор роли (как в roles.yml и в базе). */
    public String key() { return id; }

    public String id() { return id; }

    /** Отображаемое название (MiniMessage-фрагмент из roles.yml). */
    public String displayName() { return displayName; }

    public int weight() { return weight; }

    public Set<TrustAction> actions() { return actions; }

    /** Можно ли выдать роль командой/меню (у владельца и служебных ролей — нет). */
    public boolean isGrantable() { return grantable; }

    /** false — роль удалена из конфига: прав нет, id сохраняется при записи. */
    public boolean known() { return known; }

    public boolean isOwner() { return this == OWNER || OWNER_ID.equals(id); }

    public boolean allows(TrustAction action) {
        return action != null && actions.contains(action);
    }

    /** Иерархия по весу: кто кого может назначать и снимать. */
    public boolean atLeast(TrustLevel required) {
        return required != null && weight >= required.weight;
    }

    public String name() { return id.toUpperCase(Locale.ROOT); }

    @Override
    public boolean equals(Object o) {
        return o instanceof TrustLevel other && id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() { return "TrustLevel[" + id + "]"; }

    // ---------------------------------------------------------------- реестр

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /** Роль по id или псевдониму из конфига; владелец тоже находится. */
    public static Optional<TrustLevel> parse(String raw) {
        return Optional.ofNullable(registry.find(raw));
    }

    /** Роль из базы: неизвестный id не теряется, а становится ролью-сиротой без прав. */
    public static TrustLevel ofStored(String raw) {
        TrustLevel found = registry.find(raw);
        if (found != null) return found;
        String id = normalize(raw);
        if (id.isEmpty()) return defaultRole();
        return new TrustLevel(id, id, 0, EnumSet.noneOf(TrustAction.class), false, false);
    }

    /** Роль владельца с названием из конфига. */
    public static TrustLevel owner() {
        TrustLevel owner = registry.find(OWNER_ID);
        return owner != null ? owner : OWNER;
    }

    /** Выдаваемые роли, по возрастанию веса. */
    public static TrustLevel[] grantable() {
        return registry.grantable.toArray(new TrustLevel[0]);
    }

    public static List<TrustLevel> all() { return registry.ordered; }

    /** Роль по умолчанию для /trust без уровня, автодобавления и импорта. */
    public static TrustLevel defaultRole() { return registry.defaultRole; }

    /** Роль, которую получает любой игрок во флаге public-access (null — никакой). */
    public static TrustLevel publicRole() { return registry.publicRole; }

    /**
     * Самая младшая роль, которой разрешено действие: для подсказок «нужна роль ...».
     * Сначала среди выдаваемых ролей — служебную роль игроку всё равно не выдать.
     */
    public static TrustLevel lowestWith(TrustAction action) {
        for (TrustLevel level : registry.grantable) {
            if (level.allows(action)) return level;
        }
        for (TrustLevel level : registry.ordered) {
            if (level.allows(action)) return level;
        }
        return owner();
    }

    public static void install(Registry next) {
        registry = next == null ? Registry.builtin() : next;
    }

    /** Неизменяемый снимок ролей после загрузки конфига. */
    public static final class Registry {
        private final Map<String, TrustLevel> byId;
        private final Map<String, String> aliases;
        private final List<TrustLevel> ordered;
        private final List<TrustLevel> grantable;
        private final TrustLevel defaultRole;
        private final TrustLevel publicRole;

        public Registry(Map<String, TrustLevel> roles, Map<String, String> aliases,
                        String defaultRoleId, String publicRoleId, String ownerDisplay) {
            Map<String, TrustLevel> copy = new LinkedHashMap<>();
            TrustLevel owner = ownerDisplay == null || ownerDisplay.isBlank() || ownerDisplay.equals(OWNER.displayName)
                    ? OWNER : new TrustLevel(OWNER_ID, ownerDisplay, Integer.MAX_VALUE,
                    EnumSet.allOf(TrustAction.class), false, true);
            roles.forEach((id, role) -> {
                if (!OWNER_ID.equals(normalize(id))) copy.put(normalize(id), role);
            });
            List<TrustLevel> sorted = new ArrayList<>(copy.values());
            sorted.sort((a, b) -> Integer.compare(a.weight, b.weight));
            List<TrustLevel> withOwner = new ArrayList<>(sorted);
            withOwner.add(owner);
            copy.put(OWNER_ID, owner);

            Map<String, String> aliasCopy = new LinkedHashMap<>();
            aliases.forEach((from, to) -> aliasCopy.put(normalize(from), normalize(to)));

            this.byId = Map.copyOf(copy);
            this.aliases = Map.copyOf(aliasCopy);
            this.ordered = List.copyOf(withOwner);
            this.grantable = sorted.stream().filter(TrustLevel::isGrantable).toList();
            TrustLevel def = find(defaultRoleId);
            if (def == null || def.isOwner()) def = grantable.isEmpty() ? (sorted.isEmpty() ? owner : sorted.get(0))
                    : grantable.get(grantable.size() - 1);
            this.defaultRole = def;
            TrustLevel pub = publicRoleId == null || publicRoleId.isBlank() ? null : find(publicRoleId);
            this.publicRole = pub == null || pub.isOwner() ? null : pub;
        }

        TrustLevel find(String raw) {
            String id = normalize(raw);
            if (id.isEmpty()) return null;
            TrustLevel direct = byId.get(id);
            if (direct != null) return direct;
            String alias = aliases.get(id);
            return alias == null ? null : byId.get(alias);
        }

        /** Стандартный набор ролей — ровно поведение прежних фиксированных уровней. */
        public static Registry builtin() {
            Map<String, TrustLevel> roles = new LinkedHashMap<>();
            for (TrustLevel level : defaults()) roles.put(level.id, level);
            return new Registry(roles, Map.of(), "build", "container", null);
        }

        public static List<TrustLevel> defaults() {
            EnumSet<TrustAction> access = EnumSet.of(TrustAction.INTERACT, TrustAction.MENU, TrustAction.HOME,
                    TrustAction.VIEW_MEMBERS, TrustAction.GLOW, TrustAction.EFFECTS, TrustAction.ENTRY);
            EnumSet<TrustAction> container = EnumSet.copyOf(access);
            container.add(TrustAction.CONTAINER);
            EnumSet<TrustAction> build = EnumSet.copyOf(container);
            build.add(TrustAction.BUILD);
            build.add(TrustAction.ENTITY);
            EnumSet<TrustAction> manager = EnumSet.copyOf(build);
            manager.addAll(EnumSet.of(TrustAction.MANAGE, TrustAction.FLAGS, TrustAction.MEMBERS,
                    TrustAction.BAN, TrustAction.RENAME, TrustAction.ALERTS, TrustAction.UPGRADE));
            return List.of(
                    new TrustLevel("access", "Доступ", 10, access, true),
                    new TrustLevel("container", "Сундуки", 20, container, true),
                    new TrustLevel("build", "Строительство", 30, build, true),
                    new TrustLevel("manager", "Управляющий", 40, manager, true));
        }
    }
}
