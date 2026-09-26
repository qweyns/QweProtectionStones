package org.qweyns.qweprotectstones.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.qweyns.qweprotectstones.config.Tunables.TrustAction;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class InteractRulesTest {

    @Test
    void patternsMatchLikeDocumented() {
        assertTrue(MaterialPatterns.matches("*_BUCKET", "LAVA_BUCKET"));
        assertTrue(MaterialPatterns.matches("POTTED_*", "POTTED_CACTUS"));
        assertTrue(MaterialPatterns.matches("*CANDLE*", "RED_CANDLE_CAKE"));
        assertTrue(MaterialPatterns.matches("*", "STONE"));
        assertFalse(MaterialPatterns.matches("BUCKET", "LAVA_BUCKET"));
    }

    @Test
    void rulesFollowConfigOrderAndDefaults() throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString("""
                protection:
                  interact:
                    keep-item-use: false
                    items:
                      build: ["*_BUCKET"]
                    blocks:
                      interact: [LEVER]
                      build: [LEVER, CAKE]
                    default-block-action: interact
                    entities:
                      entity: [ARMOR_STAND]
                    default-entity-action: build
                """);
        InteractRules rules = InteractRules.load(cfg, Logger.getAnonymousLogger());
        assertEquals(TrustAction.BUILD, rules.forItem(Material.WATER_BUCKET));
        assertNull(rules.forItem(Material.BREAD));
        assertNull(rules.forItem(Material.AIR));
        assertEquals(TrustAction.INTERACT, rules.forBlock(Material.LEVER));
        assertEquals(TrustAction.BUILD, rules.forBlock(Material.CAKE));
        assertNull(rules.forBlock(Material.STONE));
        assertEquals(TrustAction.INTERACT, rules.defaultBlock());
        assertEquals(TrustAction.ENTITY, rules.forEntity(EntityType.ARMOR_STAND));
        assertEquals(TrustAction.BUILD, rules.forEntity(EntityType.COW));
        assertFalse(rules.keepItemUse());
    }
}
