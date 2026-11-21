package com.jeib.model;

import java.time.LocalDate;
import java.time.YearMonth;

public record RecurringPayment(String id, String name, String categoryId, long amountMinor,
                               SupportedCurrency currency, int dayOfMonth) {

    public LocalDate nextDue(LocalDate today) {
        YearMonth month = YearMonth.from(today);
        LocalDate due = inMonth(month);
        return due.isBefore(today) ? inMonth(month.plusMonths(1)) : due;
    }

    private LocalDate inMonth(YearMonth month) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }
}
