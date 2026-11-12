package com.jeib.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class LeftPerDayTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    @ParameterizedTest(name = "on {0}: {1} left → {2} per day, {3} days")
    @CsvSource({
            "2026-11-12, 1900000, 100000, 19",  // Nov has 30 days: 12th..30th = 19 days, today included
            "2026-11-01, 3000000, 100000, 30",  // first day: the whole month
            "2026-11-30,   50000,  50000,  1",  // last day: all of it today
            "2026-11-12,     100,      5, 19",  // 100 / 19 = 5.26 → rounded down, never more than you have
            "2026-11-12,       0,      0, 19",  // limit used up exactly
            "2026-11-12, -250000,      0, 19",  // over the limit: nothing per day, not a negative number
    })
    void currentMonth(String today, long remaining, long perDay, int days) {
        LeftPerDay result = LeftPerDay.of(remaining, NOVEMBER, LocalDate.parse(today));

        assertEquals(perDay, result.perDayMinor());
        assertEquals(days, result.daysLeft());
    }

    @Test
    void aFutureMonthCountsAllItsDays() {
        LeftPerDay february = LeftPerDay.of(2_900_000, YearMonth.of(2028, 2), LocalDate.parse("2026-11-12"));

        assertEquals(29, february.daysLeft()); // 2028 is a leap year
        assertEquals(100_000, february.perDayMinor());
    }

    @Test
    void aPastMonthHasNoPerDay() {
        assertNull(LeftPerDay.of(100_000, YearMonth.of(2026, 10), LocalDate.parse("2026-11-01")));
    }
}
