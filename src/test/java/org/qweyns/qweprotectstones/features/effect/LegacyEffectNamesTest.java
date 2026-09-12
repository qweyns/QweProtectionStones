package org.qweyns.qweprotectstones.features.effect;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyEffectNamesTest {

    @Test
    void bukkitConstantNamesMapToRegistryKeys() {
        Map<String, String> legacy = EffectManager.LEGACY_EFFECT_NAMES;
        assertEquals("slowness", legacy.get("slow"));
        assertEquals("haste", legacy.get("fast_digging"));
        assertEquals("mining_fatigue", legacy.get("slow_digging"));
        assertEquals("strength", legacy.get("increase_damage"));
        assertEquals("resistance", legacy.get("damage_resistance"));
        assertEquals("jump_boost", legacy.get("jump"));
        assertEquals("nausea", legacy.get("confusion"));
        assertEquals("instant_health", legacy.get("heal"));
        assertEquals("instant_damage", legacy.get("harm"));
    }
}
