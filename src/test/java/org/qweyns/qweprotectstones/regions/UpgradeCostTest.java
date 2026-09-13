package org.qweyns.qweprotectstones.regions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpgradeCostTest {

    @Test
    void noUpgradeCostsNothing() {
        assertEquals(0, UpgradeCost.calculate(3, 3, 3, 2, 1));
        assertEquals(0, UpgradeCost.calculate(5, 2, 3, 2, 1), "понижение уровня бесплатно и бессмысленно");
    }

    @Test
    void eachLevelCostsMultiplierPlusTax() {
        // Уровни 2 и 3 при множителе 3 и налоге 2: (2*3+2) + (3*3+2) = 8 + 11
        assertEquals(19, UpgradeCost.calculate(1, 3, 3, 2, 1));
        // Один уровень: 4*3+2
        assertEquals(14, UpgradeCost.calculate(3, 4, 3, 2, 1));
    }

    @Test
    void penaltyMultipliesTotal() {
        assertEquals(38, UpgradeCost.calculate(1, 3, 3, 2, 2));
    }

    @Test
    void penaltyBelowOrEqualOneIsIgnored() {
        assertEquals(19, UpgradeCost.calculate(1, 3, 3, 2, 1));
        assertEquals(19, UpgradeCost.calculate(1, 3, 3, 2, 0));
        assertEquals(19, UpgradeCost.calculate(1, 3, 3, 2, -3));
    }

    @Test
    void negativeMultiplierAndTaxAreClamped() {
        // Множитель падает до 1, налог до 0: уровни 2 и 3 стоят 2 + 3
        assertEquals(5, UpgradeCost.calculate(1, 3, -7, -100, 1));
    }

    @Test
    void bigLevelsDoNotOverflowInt() {
        // 100 уровней по ~1 млрд не должны терять старшие биты.
        long cost = UpgradeCost.calculate(0, 99, 10_000_000, 0, 1);
        assertEquals(49_500_000_000L, cost);
    }
}
