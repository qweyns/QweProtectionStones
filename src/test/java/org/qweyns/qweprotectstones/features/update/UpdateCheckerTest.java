package org.qweyns.qweprotectstones.features.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @Test
    void comparesNumericParts() {
        assertTrue(UpdateChecker.isNewer("1.2.0", "1.1.9"));
        assertTrue(UpdateChecker.isNewer("v2.0", "1.9"));
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0"));
        assertFalse(UpdateChecker.isNewer("1.1", "1.1.0"));
        assertFalse(UpdateChecker.isNewer("1.0-SNAPSHOT", "1.0"));
        assertFalse(UpdateChecker.isNewer("1.2.3", "1.2.3"));
    }
}
