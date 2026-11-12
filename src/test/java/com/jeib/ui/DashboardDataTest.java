package com.jeib.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.model.Category;
import com.jeib.model.LimitRules;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.TransactionType;
import com.jeib.service.BudgetService.LimitLine;
import com.jeib.service.BudgetService.MonthBudget;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** What the Dashboard shows, from plain data. No database, no JavaFX. */
class DashboardDataTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final LocalDate NOV_12 = LocalDate.parse("2026-11-12");

    private static Category category(String name, int color) {
        return new Category(name.toLowerCase(), name, TransactionType.EXPENSE, color, false);
    }

    private static LimitLine line(Category category, long spent, Long limit) {
        return new LimitLine(category, spent, limit, LimitRules.status(spent, limit),
                limit == null ? 0 : LimitRules.percentUsed(spent, limit));
    }

    private static MonthBudget budget(YearMonth month, Long overallLimit, LimitLine... lines) {
        long total = List.of(lines).stream().mapToLong(LimitLine::spentMinor).sum();
        return new MonthBudget(month, SupportedCurrency.RUB, line(null, total, overallLimit), List.of(lines), Set.of());
    }

    @Test
    void slicesAreTheCategoriesWithSpendingBiggestFirst() {
        DashboardData data = DashboardData.from(budget(NOVEMBER, null,
                line(category("Food", 0), 30_000, null),
                line(category("Rent", 1), 60_000, null),
                line(category("Health", 7), 0, null),
                line(category("Transport", 3), 10_000, null)), NOV_12);

        assertEquals(List.of("Rent", "Food", "Transport"),
                data.slices().stream().map(s -> s.category().name()).toList());
        assertEquals(List.of(60, 30, 10), data.slices().stream().map(DashboardData.Slice::percent).toList());
    }

    @Test
    void withoutAnOverallLimitThereIsNoPerDay() {
        DashboardData data = DashboardData.from(budget(NOVEMBER, null, line(category("Food", 0), 100, null)), NOV_12);

        assertNull(data.leftPerDay());
        assertFalse(data.monthOver());
    }

    @Test
    void withAnOverallLimitPerDayUsesWhatIsLeft() {
        // 25,000 limit, 6,000 spent → 19,000 left over 19 days (Nov 12-30) = 1,000 per day.
        DashboardData data = DashboardData.from(budget(NOVEMBER, 2_500_000L,
                line(category("Food", 0), 600_000, null)), NOV_12);

        assertEquals(100_000, data.leftPerDay().perDayMinor());
        assertEquals(19, data.leftPerDay().daysLeft());
    }

    @Test
    void aPastMonthIsOverAndHasNoPerDay() {
        DashboardData data = DashboardData.from(budget(YearMonth.of(2026, 10), 2_500_000L,
                line(category("Food", 0), 600_000, null)), NOV_12);

        assertTrue(data.monthOver());
        assertNull(data.leftPerDay());
    }

    @Test
    void limitsShowTheMostUrgentFirstAndAtMostFive() {
        List<LimitLine> lines = new ArrayList<>();
        lines.add(line(category("Calm", 0), 10, 100L));        // 10% OK
        lines.add(line(category("Near", 1), 85, 100L));        // 85% WARNING
        lines.add(line(category("Over", 2), 120, 100L));       // 120% EXCEEDED
        lines.add(line(category("Busy", 3), 70, 100L));        // 70% OK
        lines.add(line(category("NoLimit", 4), 999, null));    // left out
        lines.add(line(category("Closer", 5), 95, 100L));      // 95% WARNING
        lines.add(line(category("Quiet", 6), 1, 100L));        // 1% OK: sixth, cut

        DashboardData data = DashboardData.from(
                budget(NOVEMBER, null, lines.toArray(LimitLine[]::new)), NOV_12);

        assertEquals(List.of("Over", "Closer", "Near", "Busy", "Calm"),
                data.limits().stream().map(l -> l.category().name()).toList());
    }

    @Test
    void anEmptyMonthHasNoSlices() {
        DashboardData data = DashboardData.from(budget(NOVEMBER, null, line(category("Food", 0), 0, null)), NOV_12);

        assertTrue(data.slices().isEmpty());
    }
}
