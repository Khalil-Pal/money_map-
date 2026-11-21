package com.jeib.ui;

import com.jeib.model.Category;
import com.jeib.model.LeftPerDay;
import com.jeib.model.LimitStatus;
import com.jeib.model.SupportedCurrency;
import com.jeib.service.BudgetService.CategorySpending;
import com.jeib.service.BudgetService.LimitLine;
import com.jeib.service.BudgetService.MonthBudget;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Everything the Dashboard shows for the selected month, worked out from plain data, so it's
 * easy to test.
 *
 * @param leftPerDay null without an overall limit or when the month is over
 * @param slices     categories with spending, biggest first (the donut and its legend)
 * @param limits     category limits that are set, most urgent first, at most {@link #MAX_LIMITS}
 */
public record DashboardData(
        YearMonth month,
        SupportedCurrency mainCurrency,
        LimitLine overall,
        LeftPerDay leftPerDay,
        boolean monthOver,
        List<Slice> slices,
        List<LimitLine> limits,
        Set<SupportedCurrency> unconverted) {

    static final int MAX_LIMITS = 5;

    /** One category's share of the month's spending; percent is rounded to whole numbers. */
    public record Slice(Category category, long amountMinor, int percent) {
    }

    public static DashboardData from(MonthBudget budget, LocalDate today) {
        LimitLine overall = budget.overall();
        boolean monthOver = budget.month().isBefore(YearMonth.from(today));
        LeftPerDay leftPerDay = overall.limitMinor() == null || monthOver
                ? null
                : LeftPerDay.of(overall.remainingMinor(), budget.month(), today);

        List<LimitLine> limits = budget.categories().stream()
                .filter(line -> line.limitMinor() != null)
                .sorted(Comparator.comparingInt((LimitLine line) -> urgency(line.status()))
                        .thenComparing(Comparator.comparingInt(LimitLine::percent).reversed()))
                .limit(MAX_LIMITS)
                .toList();

        List<CategorySpending> spending = budget.categories().stream()
                .map(line -> new CategorySpending(line.category(), line.spentMinor()))
                .toList();
        return new DashboardData(budget.month(), budget.mainCurrency(), overall, leftPerDay,
                monthOver, slices(spending, overall.spentMinor()), limits, budget.unconverted());
    }

    private static List<Slice> slices(List<CategorySpending> spending, long total) {
        if (total == 0) {
            return List.of();
        }
        return spending.stream()
                .filter(line -> line.spentMinor() > 0)
                .sorted(Comparator.comparingLong(CategorySpending::spentMinor).reversed())
                .map(line -> new Slice(line.category(), line.spentMinor(), BigDecimal.valueOf(line.spentMinor()).movePointRight(2)
                        .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP).intValueExact()))
                .toList();
    }

    private static int urgency(LimitStatus status) {
        return switch (status) {
            case EXCEEDED -> 0;
            case WARNING -> 1;
            case OK -> 2;
            case NO_LIMIT -> 3;
        };
    }
}
