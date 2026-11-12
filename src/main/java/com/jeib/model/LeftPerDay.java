package com.jeib.model;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * "Left per day" (decided Nov 12, 2026: based on the overall limit only):
 * {@code (limit − spent) ÷ days left in the month, today included}, in minor units.
 *
 * <p>Rounded <em>down</em>, so the app never says you can spend a kopeck more than you have. A
 * future month counts all its days; a past month has no "per day" any more.
 *
 * @param perDayMinor 0 when the limit is already used up
 * @param daysLeft    days from today to the end of the month, today included (at least 1)
 */
public record LeftPerDay(long perDayMinor, int daysLeft) {

    /** @return null when {@code month} is already over */
    public static LeftPerDay of(long remainingMinor, YearMonth month, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        if (month.isBefore(current)) {
            return null;
        }
        int daysLeft = month.equals(current) ? month.lengthOfMonth() - today.getDayOfMonth() + 1 : month.lengthOfMonth();
        long perDay = remainingMinor <= 0 ? 0 : remainingMinor / daysLeft;
        return new LeftPerDay(perDay, daysLeft);
    }
}
