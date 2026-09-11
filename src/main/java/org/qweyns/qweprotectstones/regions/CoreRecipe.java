package org.qweyns.qweprotectstones.regions;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public record CoreRecipe(List<String> pattern, Map<Character, Material> ingredients, int resultAmount) {

    public CoreRecipe {
        pattern = List.copyOf(pattern);
        ingredients = Map.copyOf(ingredients);
        resultAmount = Math.max(1, resultAmount);
    }
}
