package org.qweyns.qweprotectstones.features.importer;

import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.regions.RegionFlag;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты маппинга флагов WorldGuard на флаги плагина при импорте.
 */
class WgFlagsTest {

    @Test
    void mapValueПереводитЛогическиеЗначения() {
        assertTrue(WgFlags.mapValue("allow"));
        assertTrue(WgFlags.mapValue("ALLOW"));
        assertTrue(WgFlags.mapValue(" true "));
        assertFalse(WgFlags.mapValue("deny"));
        assertFalse(WgFlags.mapValue("DENY"));
        assertFalse(WgFlags.mapValue("false"));
    }

    @Test
    void mapValueПропускаетСтроковыеИПустые() {
        assertNull(WgFlags.mapValue(null));
        assertNull(WgFlags.mapValue(""));
        // строковые флаги WorldGuard (текст приветствия) не переносятся
        assertNull(WgFlags.mapValue("Добро пожаловать!"));
        assertNull(WgFlags.mapValue("members"));
    }

    @Test
    void resolveНаходитСуществующиеФлаги() {
        assertEquals(RegionFlag.PVP, WgFlags.resolve("PVP"));
        assertEquals(RegionFlag.PVP, WgFlags.resolve("pvp"));
        assertEquals(RegionFlag.PISTONS_FROM_OUTSIDE, WgFlags.resolve("pistons_from_outside"));
    }

    @Test
    void resolveОтклоняетНесуществующие() {
        assertNull(WgFlags.resolve(null));
        assertNull(WgFlags.resolve("нет-такого-флага"));
        assertNull(WgFlags.resolve(""));
    }

    @Test
    void дефолтныйМаппингРезолвитсяЦеликом() {
        // каждый ключ и значение дефолтного маппинга обязаны существовать
        WgFlags.DEFAULT_MAPPING.forEach((wg, our) -> {
            assertNotNull(wg);
            assertEquals(wg, wg.toLowerCase(), "ключи хранятся в нижнем регистре");
            assertNotNull(WgFlags.resolve(our), "неизвестный флаг в дефолтном маппинге: " + our);
        });
    }

    @Test
    void normalizeПриводитКлючиКНижнемуРегиструИЧистит() {
        Map<String, String> raw = new HashMap<>();
        raw.put("PVP", " PVP ");
        raw.put("Fire-Spread", "FIRE_SPREAD");
        raw.put(" entry ", null);
        raw.put(null, "ENTRY");

        Map<String, String> normalized = WgFlags.normalize(raw);
        assertEquals(2, normalized.size());
        assertEquals("PVP", normalized.get("pvp"));
        assertEquals("FIRE_SPREAD", normalized.get("fire-spread"));
        assertNull(normalized.get("entry"));
    }
}
