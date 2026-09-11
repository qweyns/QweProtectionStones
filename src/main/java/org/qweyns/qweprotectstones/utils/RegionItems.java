package org.qweyns.qweprotectstones.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.qweyns.qweprotectstones.QweProtectStones;
import org.qweyns.qweprotectstones.regions.Region;
import org.qweyns.qweprotectstones.regions.RegionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Единая точка сборки предмета-ядра: PDC-тег типа, накопленная прочность
 * и внешний вид (имя, описание, свечение — секция item в regions.yml).
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
     * @param tagType ставить ли PDC-тег типа: для типов с
     *               {@code restrict-obtaining: true} — всегда (без тега блок
     *               приват не создаст), для обычных — по настройке
     *               {@code settings.core-item-tags};
     * @param carriedDurability накопленная прочность или null — не переносить;
     * @param styled применять ли внешний вид из секции item (имя, описание,
     *               свечение). Включено для выдачи командой и крафта;
     *               при поломке ядра — только у ограниченных типов,
     *               обычные возвращаются как обычный блок.
     */
    public static ItemStack core(QweProtectStones plugin, RegionType type, int amount,
                                 Integer carriedDurability, boolean tagType, boolean styled) {
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

        if (styled) {
            // Имя предмета (MiniMessage); пусто — стандартное имя блока.
            if (!type.itemName().isEmpty()) {
                meta.displayName(ColorUtil.formatItemComponent(type.itemName()));
            }
            if (!type.itemLore().isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : type.itemLore()) {
                    lore.add(ColorUtil.formatItemComponent(line));
                }
                meta.lore(lore);
            }
            // Свечение, как у зачарованного предмета.
            if (type.itemGlow()) {
                meta.setEnchantmentGlintOverride(true);
            }
        }

        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Предмет-ядро, возвращаемый при удалении привата (поломка блока или
     * /ps delete): PDC-теги прочности — по настройке, ограниченные типы
     * (restrict-obtaining) — с тегом и в полном оформлении, чтобы покупка
     * не превращалась в обычный блок.
     */
    public static ItemStack returnCore(QweProtectStones plugin, RegionType type, Region region) {
        boolean tags = plugin.getConfigManager().getConfig().getBoolean("settings.core-item-tags", true);
        Integer durability = region != null
                && plugin.getConfigManager().getConfig().getBoolean("settings.return-durability", true)
                && tags
                ? region.getDurability() : null;
        return core(plugin, type, 1, durability, tags || type.restrictObtaining(), type.restrictObtaining());
    }

    /** Тип из PDC-тега предмета или null. Материал проверяет вызывающий. */
    public static String taggedTypeId(QweProtectStones plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(typeKey(plugin), PersistentDataType.STRING);
    }
}
