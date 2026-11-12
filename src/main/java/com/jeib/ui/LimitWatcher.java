package com.jeib.ui;

import com.jeib.model.LimitStatus;
import com.jeib.service.BudgetService;
import com.jeib.service.BudgetService.LimitLine;
import com.jeib.service.BudgetService.MonthBudget;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.concurrent.Task;

/**
 * The in-app limit alert: after every data change it compares this month's limit statuses with the
 * last ones it saw, and reports a limit that just became "near" (80%+) or "exceeded" (100%+). The
 * first check after start only records the statuses, so opening the app doesn't nag.
 */
public final class LimitWatcher {

    static final String OVERALL = "overall";

    private final BudgetService budgets;
    private final Clock clock;
    private final Consumer<String> report;
    private Map<String, LimitStatus> last; // null until the first check

    public LimitWatcher(BudgetService budgets, Clock clock, Consumer<String> report) {
        this.budgets = budgets;
        this.clock = clock;
        this.report = report;
    }

    /** Checks the current month in the background; reports anything that got worse. */
    public void check() {
        YearMonth month = YearMonth.now(clock);
        Task<MonthBudget> task = new Task<>() {
            @Override
            protected MonthBudget call() throws Exception {
                return budgets.forMonth(month);
            }
        };
        task.setOnSucceeded(event -> {
            MonthBudget now = task.getValue();
            if (last != null) {
                List<String> alerts = alerts(last, now);
                if (!alerts.isEmpty()) {
                    report.accept(String.join("  ", alerts));
                }
            }
            last = statuses(now);
        });
        // On failure (e.g. no main currency yet) keep the old snapshot and stay quiet.
        Thread.ofVirtual().name("jeib-limit-watch").start(task);
    }

    static Map<String, LimitStatus> statuses(MonthBudget budget) {
        Map<String, LimitStatus> result = new HashMap<>();
        result.put(OVERALL, budget.overall().status());
        budget.categories().forEach(line -> result.put(line.category().id(), line.status()));
        return result;
    }

    /** Messages for limits that moved up to WARNING or EXCEEDED; most serious first. */
    static List<String> alerts(Map<String, LimitStatus> before, MonthBudget now) {
        List<String> exceeded = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<LimitLine> lines = new ArrayList<>(now.categories());
        lines.addFirst(now.overall());
        for (LimitLine line : lines) {
            String key = line.isOverall() ? OVERALL : line.category().id();
            LimitStatus previous = before.getOrDefault(key, LimitStatus.NO_LIMIT);
            if (line.status() == LimitStatus.EXCEEDED && previous != LimitStatus.EXCEEDED) {
                exceeded.add(line.isOverall()
                        ? "You're over your monthly limit (" + line.percent() + "%)."
                        : line.category().name() + " is over its monthly limit (" + line.percent() + "%).");
            } else if (line.status() == LimitStatus.WARNING
                    && previous != LimitStatus.WARNING && previous != LimitStatus.EXCEEDED) {
                warnings.add(line.isOverall()
                        ? "You've used " + line.percent() + "% of your monthly limit."
                        : line.category().name() + " is at " + line.percent() + "% of its monthly limit.");
            }
        }
        exceeded.addAll(warnings);
        return exceeded;
    }
}
