package org.qweyns.qweprotectstones.regions;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * Собственный крафт блока-ядра (секция {@code recipe} у типа в regions.yml).
 *
 * <p>Если у типа рецепта нет — блок получается как обычно, ведь это обычный
 * блок Minecraft. Рецепт имеет смысл для {@code source: SURVIVAL}:
 * «покупные» типы крафтом не добываются.</p>
 *
 * @param pattern схема строками по три символа, пробел — пустая клетка;
 * @param ingredients что означает каждая буква схемы;
 * @param resultAmount сколько блоков-ядер выдаёт один крафт.
 */
public record CoreRecipe(List<String> pattern, Map<Character, Material> ingredients, int resultAmount) {

    public CoreRecipe {
        pattern = List.copyOf(pattern);
        ingredients = Map.copyOf(ingredients);
        resultAmount = Math.max(1, resultAmount);
    }
}
