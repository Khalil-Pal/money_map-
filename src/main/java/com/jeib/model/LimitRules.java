package com.jeib.model;

/**
 * Monthly limit rules: at or above 80% of the limit is a warning, at or above 100% is exceeded.
 * Works on minor units (cents) with integer math only, so 80.0% is never mistaken for 79.99%.
 */
public final class LimitRules {

    public static final int WARNING_PERCENT = 80;

    private LimitRules() {
    }

    /**
     * @param spentCents sum of non-deleted EXPENSE transactions for the category and month
     * @param limitCents the monthly limit, or {@code null} when none is set
     */
    public static LimitStatus status(long spentCents, Long limitCents) {
        if (limitCents == null || limitCents <= 0) {
            return LimitStatus.NO_LIMIT;
        }
        long spentScaled = Math.multiplyExact(spentCents, 100);
        if (spentScaled >= Math.multiplyExact(limitCents, 100)) {
            return LimitStatus.EXCEEDED;
        }
        if (spentScaled >= Math.multiplyExact(limitCents, WARNING_PERCENT)) {
            return LimitStatus.WARNING;
        }
        return LimitStatus.OK;
    }

    /** Percentage of the limit used, rounded half up, for display ("86%"). */
    public static int percentUsed(long spentCents, long limitCents) {
        if (limitCents <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return Math.toIntExact((Math.multiplyExact(spentCents, 100) + limitCents / 2) / limitCents);
    }
}
