package com.jeib.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jeib.model.Category;
import com.jeib.model.LimitStatus;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.TransactionType;
import com.jeib.service.BudgetService.LimitLine;
import com.jeib.service.BudgetService.MonthBudget;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Which status changes make an alert, and what it says. Pure: no database, no JavaFX. */
class LimitWatcherTest {

    private static final Category FOOD = new Category("food", "Food", TransactionType.EXPENSE, 0, false);
    private static final Category RENT = new Category("rent", "Rent", TransactionType.EXPENSE, 1, false);

    private static LimitLine line(Category category, LimitStatus status, int percent) {
        return new LimitLine(category, 0, status == LimitStatus.NO_LIMIT ? null : 100L, status, percent);
    }

    private static MonthBudget month(LimitLine overall, LimitLine... categories) {
        return new MonthBudget(YearMonth.of(2026, 11), SupportedCurrency.RUB, overall, List.of(categories), Set.of());
    }

    private static final LimitLine NO_OVERALL = line(null, LimitStatus.NO_LIMIT, 0);

    @Test
    void goingFromOkToNearOrOverIsReported() {
        Map<String, LimitStatus> before = Map.of(LimitWatcher.OVERALL, LimitStatus.NO_LIMIT,
                "food", LimitStatus.OK, "rent", LimitStatus.OK);

        List<String> alerts = LimitWatcher.alerts(before,
                month(NO_OVERALL, line(FOOD, LimitStatus.WARNING, 86), line(RENT, LimitStatus.EXCEEDED, 104)));

        // The more serious one comes first.
        assertEquals(List.of("Rent is over its monthly limit (104%).", "Food is at 86% of its monthly limit."), alerts);
    }

    @Test
    void staysTheSameOrGetsBetterIsQuiet() {
        Map<String, LimitStatus> before = Map.of(LimitWatcher.OVERALL, LimitStatus.WARNING,
                "food", LimitStatus.EXCEEDED, "rent", LimitStatus.WARNING);

        List<String> alerts = LimitWatcher.alerts(before, month(line(null, LimitStatus.OK, 50),
                line(FOOD, LimitStatus.EXCEEDED, 130), line(RENT, LimitStatus.WARNING, 90)));

        assertEquals(List.of(), alerts);
    }

    @Test
    void nearToOverIsReportedAgain() {
        Map<String, LimitStatus> before = Map.of(LimitWatcher.OVERALL, LimitStatus.WARNING, "food", LimitStatus.OK);

        List<String> alerts = LimitWatcher.alerts(before,
                month(line(null, LimitStatus.EXCEEDED, 101), line(FOOD, LimitStatus.OK, 10)));

        assertEquals(List.of("You're over your monthly limit (101%)."), alerts);
    }

    @Test
    void theOverallLimitHasItsOwnWording() {
        List<String> alerts = LimitWatcher.alerts(Map.of(), month(line(null, LimitStatus.WARNING, 82)));

        assertEquals(List.of("You've used 82% of your monthly limit."), alerts);
    }

    @Test
    void statusesAreRememberedByCategoryId() {
        Map<String, LimitStatus> statuses = LimitWatcher.statuses(
                month(line(null, LimitStatus.OK, 10), line(FOOD, LimitStatus.WARNING, 85)));

        assertEquals(Map.of(LimitWatcher.OVERALL, LimitStatus.OK, "food", LimitStatus.WARNING), statuses);
    }
}
