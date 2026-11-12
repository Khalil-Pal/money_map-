package com.jeib.ui;

import com.jeib.service.BudgetService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;

/** State for the Dashboard. Reloads with the month, when the database is ready, and after any change. */
public final class DashboardViewModel {

    private final BudgetService budgets;
    private final ReadOnlyObjectProperty<YearMonth> month;
    private final Clock clock;

    private final ObjectProperty<DashboardData> data = new SimpleObjectProperty<>();
    private final StringProperty message = new SimpleStringProperty("");

    public DashboardViewModel(BudgetService budgets, ReadOnlyObjectProperty<YearMonth> month,
                              ReadOnlyBooleanProperty ready, Clock clock) {
        this.budgets = budgets;
        this.month = month;
        this.clock = clock;
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
        LocalDate today = LocalDate.now(clock);
        Task<DashboardData> task = new Task<>() {
            @Override
            protected DashboardData call() throws Exception {
                return DashboardData.from(budgets.forMonth(requested), today);
            }
        };
        task.setOnSucceeded(event -> {
            if (requested.equals(month.get())) {
                data.set(task.getValue());
                message.set("");
            }
        });
        task.setOnFailed(event -> {
            if (requested.equals(month.get())) {
                message.set(task.getException().getMessage());
            }
        });
        Thread.ofVirtual().name("jeib-dashboard").start(task);
    }

    public ReadOnlyObjectProperty<DashboardData> dataProperty() {
        return data;
    }

    public ReadOnlyStringProperty messageProperty() {
        return message;
    }
}
