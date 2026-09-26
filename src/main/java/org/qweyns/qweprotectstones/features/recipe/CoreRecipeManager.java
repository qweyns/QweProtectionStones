package org.qweyns.qweprotectstones.features.recipe;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.CoreRecipe;
import org.qweyns.qweprotectstones.regions.RegionType;
import org.qweyns.qweprotectstones.utils.RegionItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CoreRecipeManager {

    private final QweProtectStones plugin;

    private final List<NamespacedKey> registered = new ArrayList<>();

    public CoreRecipeManager(QweProtectStones plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        for (NamespacedKey key : registered) {
            Bukkit.removeRecipe(key);
        }
        registered.clear();

        int count = 0;
        for (RegionType type : plugin.getRegionTypes().all()) {
            CoreRecipe recipe = type.recipe();
            if (recipe == null) continue;
            if (register(type, recipe)) count++;
        }
        if (count > 0) {
            plugin.getLogger().info("Зарегистрировано крафтов блоков-ядер: " + count);
        }
    }

    private boolean register(RegionType type, CoreRecipe recipe) {
        NamespacedKey key = keyOf(type);

        boolean tags = plugin.getConfigManager().getConfig().getBoolean("settings.core-item-tags", true);
        ShapedRecipe shaped = new ShapedRecipe(key,
                RegionItems.core(plugin, type, recipe.resultAmount(), null,
                        tags || type.restrictObtaining(), true));

        shaped.shape(recipe.pattern().toArray(new String[0]));
        recipe.ingredients().forEach(shaped::setIngredient);

        try {
            Bukkit.addRecipe(shaped);
        } catch (IllegalStateException e) {
            plugin.getLogger().warning("Не удалось зарегистрировать крафт " + type.id() + ": " + e.getMessage());
            return false;
        }

        registered.add(key);
        return true;
    }

    private NamespacedKey keyOf(RegionType type) {
        // Ключи рецептов допускают только строчные буквы, цифры и знак '_'.
        return new NamespacedKey(plugin, "core_" + type.id().toLowerCase(Locale.ROOT));
    }
}
