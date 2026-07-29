package org.qweyns.qweprotectstones.regions;

/**
 * Расчёт стоимости прокачки прочности. Вынесен из меню в отдельный класс:
 * это чистая арифметика, которую нужно проверять тестами, а не глазами.
 */
public final class UpgradeCost {

    private UpgradeCost() {
    }

    /**
     * Суммарная цена перехода с уровня {@code current} на {@code target}.
     * Каждый уровень стоит {@code уровень * множитель + налог}.
     *
     * @param penaltyMultiplier множитель штрафа; 1 или меньше — штрафа нет
     */
    public static long calculate(int current, int target, int multiplier, int tax, int penaltyMultiplier) {
        if (target <= current) return 0;

        long safeMultiplier = Math.max(1, multiplier);
        long safeTax = Math.max(0, tax);

        long total = 0;
        for (int level = current + 1; level <= target; level++) {
            total += level * safeMultiplier + safeTax;
        }

        if (penaltyMultiplier > 1) total *= penaltyMultiplier;
        return Math.max(0, total);
    }
}
