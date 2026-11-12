package com.jeib.ui;

import com.jeib.service.BudgetService;
import com.jeib.service.BudgetService.MonthBudget;
import java.time.YearMonth;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;

/**
 * State for the Budgets page: the selected month's limits and how much of each is used. Reloads
 * when the month changes, when the database becomes ready, and after any data change.
 */
public final class BudgetsViewModel {

    private final BudgetService budgets;
    private final ReadOnlyObjectProperty<YearMonth> month;

    private final ObjectProperty<MonthBudget> budget = new SimpleObjectProperty<>();
    private final StringProperty message = new SimpleStringProperty("");

    public BudgetsViewModel(BudgetService budgets, ReadOnlyObjectProperty<YearMonth> month,
                            ReadOnlyBooleanProperty ready) {
        this.budgets = budgets;
        this.month = month;
        month.addListener((obs, old, now) -> {
            if (ready.get()) {
                load();
            }
        });
        ready.addListener((obs, was, isReady) -> {
            if (isReady) {
                load();
            }
        });
    }

    public void load() {
        YearMonth requested = month.get();
        Task<MonthBudget> task = new Task<>() {
            @Override
            protected MonthBudget call() throws Exception {
                return budgets.forMonth(requested);
            }
        };
        task.setOnSucceeded(event -> {
            if (requested.equals(month.get())) {
                budget.set(task.getValue());
                message.set("");
            }
        });
        task.setOnFailed(event -> {
            if (requested.equals(month.get())) {
                message.set(task.getException().getMessage());
            }
        });
        Thread.ofVirtual().name("jeib-budgets").start(task);
    }

    /** The loaded month, or null before the first load. */
    public ReadOnlyObjectProperty<MonthBudget> budgetProperty() {
        return budget;
    }

    /** An error to show instead of the page; empty normally. */
    public ReadOnlyStringProperty messageProperty() {
        return message;
    }
}
