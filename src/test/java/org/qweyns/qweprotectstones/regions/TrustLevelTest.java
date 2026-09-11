package org.qweyns.qweprotectstones.regions;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustLevelTest {

    @Test
    void levelsAreStrictlyNested() {

        for (TrustLevel given : TrustLevel.values()) {
            for (TrustLevel required : TrustLevel.values()) {
                assertEquals(given.weight() >= required.weight(),
                        given.atLeast(required),
                        given + " vs " + required);
            }
        }
    }

    @Test
    void parseIsCaseAndSpaceInsensitive() {
        assertEquals(Optional.of(TrustLevel.ACCESS), TrustLevel.parse("access"));
        assertEquals(Optional.of(TrustLevel.BUILD), TrustLevel.parse(" BUILD "));
        assertEquals(Optional.of(TrustLevel.MANAGER), TrustLevel.parse("Manager"));
        assertEquals(Optional.of(TrustLevel.OWNER), TrustLevel.parse("owner"));
    }

    @Test
    void parseRejectsUnknownValues() {
        assertTrue(TrustLevel.parse("vip").isEmpty());
        assertTrue(TrustLevel.parse("").isEmpty());
        assertTrue(TrustLevel.parse(null).isEmpty());
    }

    @Test
    void grantableExcludesOwner() {

        for (TrustLevel level : TrustLevel.grantable()) {
            assertFalse(level == TrustLevel.OWNER);
        }
        assertEquals(4, TrustLevel.grantable().length);
    }

    @Test
    void keyIsLowercase() {
        assertEquals("access", TrustLevel.ACCESS.key());
        assertEquals("owner", TrustLevel.OWNER.key());
    }
}
