package org.qweyns.qweprotectstones.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.RegionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Единая точка сборки предмета-ядра: PDC-тег типа, накопленная прочность
 * и собственное описание (например, у «покупных» приватов).
 *
 * <p>Раньше тег ставили в трёх местах независимо (/qps give, возврат при
 * поломке, клавиши дублировались строками) — теперь клавиши и логика
 * живут здесь.</p>
 */
public final class RegionItems {

    private RegionItems() {
    }

    /** PDC-тег с типом привата на предмете-ядре. */
    public static NamespacedKey typeKey(QweProtectStones plugin) {
        return new NamespacedKey(plugin, "core-type");
    }

    /** PDC-тег с накопленной прочностью (переносится при переустановке ядра). */
    public static NamespacedKey durabilityKey(QweProtectStones plugin) {
        return new NamespacedKey(plugin, "core-durability");
    }

    /**
     * Собирает блок-ядро.
     *
     * @param tagType ставить ли PDC-тег типа: для «покупных» типов — всегда
     *               (без тега блок приват не создаст), для обычных — по
     *               настройке {@code settings.core-item-tags};
     * @param carriedDurability накопленная прочность или null — не переносить.
     */
    public static ItemStack core(QweProtectStones plugin, RegionType type, int amount,
                                 Integer carriedDurability, boolean tagType) {
        ItemStack stack = new ItemStack(type.material(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        if (tagType) {
            meta.getPersistentDataContainer().set(
                    typeKey(plugin), PersistentDataType.STRING, type.id());
        }
        if (carriedDurability != null && carriedDurability > 0) {
            meta.getPersistentDataContainer().set(
                    durabilityKey(plugin), PersistentDataType.INTEGER, carriedDurability);
        }

        // Собственное описание типа — например, у покупного привата.
        if (!type.description().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : type.description()) {
                lore.add(ColorUtil.formatItemComponent(line));
            }
            meta.lore(lore);
        }

        stack.setItemMeta(meta);
        return stack;
    }

    /** Тип из PDC-тега предмета или null. Материал проверяет вызывающий. */
    public static String taggedTypeId(QweProtectStones plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(typeKey(plugin), PersistentDataType.STRING);
    }
}
