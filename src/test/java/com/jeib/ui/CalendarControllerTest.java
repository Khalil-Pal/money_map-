package com.jeib.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jeib.model.BudgetPlan;
import com.jeib.model.DateRange;
import com.jeib.model.SupportedCurrency;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CalendarControllerTest {

    @Test
    void weeksUseTheLocalesFirstWeekdayAcrossMonthAndYearBoundaries() {
        var date = LocalDate.of(2027, 1, 1);
        assertEquals(new DateRange(LocalDate.of(2026, 12, 27), LocalDate.of(2027, 1, 2)),
                CalendarController.weekDates(date, DayOfWeek.SUNDAY));
        assertEquals(new DateRange(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3)),
                CalendarController.weekDates(date, DayOfWeek.MONDAY));
    }

    @Test
    void weekCannotLoadDatesOutsideTheSupportedYears() {
        var earliest = LocalDate.of(1, 1, 1);
        var latest = LocalDate.of(9999, 12, 31);
        assertEquals(earliest, CalendarController.weekDates(earliest, DayOfWeek.SUNDAY).start());
        assertEquals(latest, CalendarController.weekDates(latest, DayOfWeek.MONDAY).end());
    }

    @Test
    void everyPlanBoundaryOnTheSameDayIsKept() {
        var first = LocalDate.of(2026, 11, 20);
        var shared = LocalDate.of(2026, 11, 23);
        var last = LocalDate.of(2026, 12, 10);
        var dates = CalendarController.budgetBoundaries(List.of(
                plan("November", first, shared), plan("December", shared, last), plan("Travel", shared, shared)));

        assertEquals(List.of(new CalendarController.Boundary("November", true)), dates.get(first));
        assertEquals(List.of(new CalendarController.Boundary("November", false),
                new CalendarController.Boundary("December", true), new CalendarController.Boundary("Travel", true),
                new CalendarController.Boundary("Travel", false)), dates.get(shared));
        assertEquals(List.of(new CalendarController.Boundary("December", false)), dates.get(last));
    }

    private static BudgetPlan plan(String name, LocalDate start, LocalDate end) {
        return new BudgetPlan(name, name, new DateRange(start, end), 1000, SupportedCurrency.USD, Set.of());
    }
}
