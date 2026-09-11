package org.qweyns.qweprotectstones.regions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Нормализация пользовательских текстов: переводы строк, пробелы, потолок
 * длины и запасные варианты названия.
 */
class RegionTextTest {

    @Test
    void normalizeNullGivesEmpty() {
        assertEquals("", RegionText.normalize(null));
    }

    @Test
    void normalizeReplacesLineBreaksWithSpaces() {
        assertEquals("первая вторая", RegionText.normalize("первая\nвторая"));
        assertEquals("первая вторая", RegionText.normalize("первая\r\nвторая"));
    }

    @Test
    void normalizeTrimsAndCollapsesEdges() {
        assertEquals("название", RegionText.normalize("   название   "));
    }

    @Test
    void normalizeTruncatesToMaxLength() {
        String longName = "а".repeat(RegionText.MAX_LENGTH + 50);

        assertEquals(RegionText.MAX_LENGTH, RegionText.normalize(longName).length());
        assertEquals(longName.substring(0, RegionText.MAX_LENGTH), RegionText.normalize(longName));
    }

    @Test
    void normalizeStripsMiniMessageTags() {
        // Кликабельные ловушки и разметка не должны выживать в пользовательском тексте.
        assertEquals("жми", RegionText.normalize("<click:run_command:'/ps trust Грифер'>жми</click>"));
        assertEquals("База", RegionText.normalize("<gradient:#FF0000:#00FF00>База</gradient>"));
    }

    @Test
    void normalizeKeepsPlainTextThatLooksLikeNothing() {
        // Строка, не похожая на тег MiniMessage, остаётся как есть.
        assertEquals("<3 домой", RegionText.normalize("<3 домой"));
        assertEquals("R&D", RegionText.normalize("R&D"));
    }

    @Test
    void normalizeStripsSectionSignCodes() {
        // § и следующий символ-код удаляются парой, одиночный § тоже исчезает.
        assertEquals("Привет", RegionText.normalize("§a§lПривет"));
        assertEquals("текст", RegionText.normalize("§текст"));
    }

    @Test
    void sanitizeAppliesBeforeLengthLimit() {
        // Теги не занимают место в лимите длины.
        String evil = "<click:run_command:'/ps delete'>x</click>" + "а".repeat(200);
        String normalized = RegionText.normalize(evil);
        assertEquals(RegionText.MAX_LENGTH, normalized.length());
        assertTrue(normalized.startsWith("x"));
    }

    @Test
    void labelPrefersDisplayNameThenOwnerThenId() {
        assertEquals("База", RegionText.label("База", "Steve", "a1b2c3d4"));
        assertEquals("Steve", RegionText.label("", "Steve", "a1b2c3d4"));
        assertEquals("a1b2c3d4", RegionText.label("", "", "a1b2c3d4"));
        assertEquals("", RegionText.label(null, null, null));
    }

    @Test
    void clearWordsAreRecognizedCaseInsensitive() {
        assertTrue(RegionText.isClearWord("clear"));
        assertTrue(RegionText.isClearWord("RESET"));
        assertTrue(RegionText.isClearWord(" off "));
        assertTrue(RegionText.isClearWord("none"));
        assertTrue(RegionText.isClearWord("сброс"));
        assertTrue(RegionText.isClearWord("Убрать"));

        assertFalse(RegionText.isClearWord("клир"));
        assertFalse(RegionText.isClearWord("название"));
        assertFalse(RegionText.isClearWord(null));
    }
}
