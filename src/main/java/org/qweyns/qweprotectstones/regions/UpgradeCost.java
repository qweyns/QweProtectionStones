package org.qweyns.qweprotectstones.regions;

public final class UpgradeCost {

    private UpgradeCost() {
    }

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
