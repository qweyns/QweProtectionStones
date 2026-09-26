package org.qweyns.qweprotectstones.config;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Разбор списков материалов и сущностей из конфига в EnumSet (проверка O(1) на горячем пути).
 * Поддерживается: точное имя, "*_BUCKET" (окончание), "POTTED_*" (начало), "*CANDLE*" (подстрока),
 * "#doors" — тег блоков или предметов Minecraft, "*" — всё.
 */
public final class MaterialPatterns {

    private MaterialPatterns() { }

    public static Set<Material> materials(Collection<String> patterns, Consumer<String> unknown) {
        EnumSet<Material> result = EnumSet.noneOf(Material.class);
        for (String raw : patterns) {
            if (raw == null || raw.isBlank()) continue;
            String pattern = raw.trim();
            if (pattern.startsWith("#")) {
                if (!addTag(result, pattern.substring(1))) unknown.accept(raw);
                continue;
            }
            int before = result.size();
            String upper = pattern.toUpperCase(Locale.ROOT);
            for (Material material : Material.values()) {
                if (!material.isLegacy() && matches(upper, material.name())) result.add(material);
            }
            if (result.size() == before && !upper.contains("*")) unknown.accept(raw);
        }
        return result;
    }

    public static Set<EntityType> entities(Collection<String> patterns, Consumer<String> unknown) {
        EnumSet<EntityType> result = EnumSet.noneOf(EntityType.class);
        for (String raw : patterns) {
            if (raw == null || raw.isBlank()) continue;
            String upper = raw.trim().toUpperCase(Locale.ROOT);
            int before = result.size();
            for (EntityType type : EntityType.values()) {
                if (type != EntityType.UNKNOWN && matches(upper, type.name())) result.add(type);
            }
            if (result.size() == before && !upper.contains("*")) unknown.accept(raw);
        }
        return result;
    }

    public static boolean matches(String pattern, String name) {
        if (pattern.equals("*")) return true;
        boolean head = pattern.startsWith("*"), tail = pattern.endsWith("*");
        if (head && tail && pattern.length() > 2) return name.contains(pattern.substring(1, pattern.length() - 1));
        if (head) return name.endsWith(pattern.substring(1));
        if (tail) return name.startsWith(pattern.substring(0, pattern.length() - 1));
        return name.equals(pattern);
    }

    private static boolean addTag(Set<Material> result, String name) {
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        if (key == null) return false;
        boolean found = false;
        try {
            Tag<Material> blocks = Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
            if (blocks != null) { result.addAll(blocks.getValues()); found = true; }
            Tag<Material> items = Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
            if (items != null) { result.addAll(items.getValues()); found = true; }
        } catch (RuntimeException | LinkageError ignored) {
            // без сервера (юнит-тесты) теги недоступны
        }
        return found;
    }
}
