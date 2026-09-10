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
