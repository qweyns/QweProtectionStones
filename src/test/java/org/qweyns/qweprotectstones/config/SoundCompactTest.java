package org.qweyns.qweprotectstones.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SoundCompactTest {

    @Test
    void legacyNamesCollideWithRegistryKeys() {
        // ровно это и требуется от индекса: старое имя == ключ реестра без разделителей
        assertEquals(SoundSetting.compact("block.note_block.hat"), SoundSetting.compact("BLOCK_NOTE_BLOCK_HAT"));
        assertEquals(SoundSetting.compact("entity.player.levelup"), SoundSetting.compact("ENTITY_PLAYER_LEVELUP"));
        assertEquals(SoundSetting.compact("block.iron_trapdoor.open"), SoundSetting.compact("BLOCK_IRON_TRAPDOOR_OPEN"));
    }
}
