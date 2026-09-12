package org.qweyns.qweprotectstones.menus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentsTest {

    @Test
    void accumulatesAllThreeCurrencies() {
        Payments payments = new Payments();
        payments.addMoney(100.5);
        payments.addMoney(49.5);
        payments.addPoints(10);
        payments.addPoints(5);
        payments.addExp(3);

        assertTrue(payments.any());
        assertEquals(150.0, payments.money(), 1e-9);
        assertEquals(15, payments.points());
        assertEquals(3, payments.exp());
    }

    @Test
    void emptyLedgerIsNotRefundable() {
        assertFalse(new Payments().any());
    }

    @Test
    void ceilCostRoundsUp() {
        assertEquals(10, Payments.ceilCost(10.0));
        assertEquals(11, Payments.ceilCost(10.2));
        assertEquals(1, Payments.ceilCost(0.1));
        assertEquals(0, Payments.ceilCost(0.0));
    }
}
