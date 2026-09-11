package org.qweyns.qweprotectstones.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Подстановка прочности в имя и описание предмета-ядра: плейсхолдеры
 * %durability% и %max_durability% работают и в name, и в lore.
 */
class RegionItemsTest {

    @Test
    void подставляетОбаПлейсхолдера() {
        assertEquals("Прочность: 5 (прокачка до 48)",
                RegionItems.durabilityPlaceholders("Прочность: %durability% (прокачка до %max_durability%)", "5", "48"));
    }

    @Test
    void работаетИВИмениПредмета() {
        assertEquals("<gradient:#F84747:#DD4141>Ядро [7/12]</gradient>",
                RegionItems.durabilityPlaceholders("<gradient:#F84747:#DD4141>Ядро [%durability%/%max_durability%]</gradient>", "7", "12"));
    }

    @Test
    void текстБезПлейсхолдеровНеТрогает() {
        assertEquals("Продаётся в магазине сервера",
                RegionItems.durabilityPlaceholders("Продаётся в магазине сервера", "5", "48"));
    }

    @Test
    void пустойИNullПроходитСквозь() {
        assertEquals("", RegionItems.durabilityPlaceholders("", "5", "48"));
        assertNull(RegionItems.durabilityPlaceholders(null, "5", "48"));
    }

    @Test
    void повторныеПодстановкиРаботают() {
        assertEquals("5 из 5", RegionItems.durabilityPlaceholders("%durability% из %durability%", "5", "48"));
    }
}
