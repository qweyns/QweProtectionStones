package org.qweyns.qweprotectstones.hooks;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;

class VaultHookTest {
    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0})
    void invalidAmountsAreRejectedBeforeCallingEconomy(double amount) {
        // Без Bukkit: некорректная сумма должна отсекаться до поиска провайдера.
        VaultHook hook = new VaultHook();
        assertFalse(hook.hasMoney(null, amount));
        assertFalse(hook.takeMoney(null, amount));
        assertFalse(hook.giveMoney(null, amount));
    }
}
