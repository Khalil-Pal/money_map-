package com.jeib.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/** Both dates are included. SQLite stores transaction dates as YYYY-MM-DD. */
public record DateRange(LocalDate start, LocalDate end) {

    public DateRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (start.isAfter(end) || start.getYear() < 1 || end.getYear() > 9999) {
            throw new IllegalArgumentException("Invalid date range");
        }
    }

    public static DateRange ofMonth(YearMonth month) {
        return new DateRange(month.atDay(1), month.atEndOfMonth());
    }
}
