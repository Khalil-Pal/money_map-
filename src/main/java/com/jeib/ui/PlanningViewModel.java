package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.Category;
import com.jeib.model.SupportedCurrency;
import com.jeib.service.PlanningService;
import com.jeib.service.ValidationException;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;

public final class PlanningViewModel {

    final SimpleObjectProperty<PlanningService.RecurringData> payments = new SimpleObjectProperty<>();
    final SimpleObjectProperty<PlanningService.GoalsData> goals = new SimpleObjectProperty<>();
    final SimpleBooleanProperty paymentsBusy = new SimpleBooleanProperty();
    final SimpleBooleanProperty goalsBusy = new SimpleBooleanProperty();
    final SimpleStringProperty paymentsError = new SimpleStringProperty("");
    final SimpleStringProperty goalsError = new SimpleStringProperty("");
    private final PlanningService service;

    public PlanningViewModel(PlanningService service) {
        this.service = service;
    }

    public void reload() {
        if (!paymentsBusy.get()) {
            run(service::recurring, payments::set, paymentsBusy, paymentsError);
        }
        if (!goalsBusy.get()) {
            run(service::goals, goals::set, goalsBusy, goalsError);
        }
    }

    void createPayment(String name, String amount, String day, Category category, SupportedCurrency currency,
                       Runnable saved) {
        updatePayments(() -> service.createPayment(name, amount, day, category, currency), saved);
    }

    void archivePayment(String id) {
        updatePayments(() -> service.archivePayment(id), () -> { });
    }

    void createGoal(String name, String amount, SupportedCurrency currency, Runnable saved) {
        updateGoals(() -> service.createGoal(name, amount, currency), saved);
    }

    void addSavings(String id, String amount) {
        updateGoals(() -> service.addSavings(id, amount), () -> { });
    }

    void archiveGoal(String id) {
        updateGoals(() -> service.archiveGoal(id), () -> { });
    }

    private void updatePayments(Write operation, Runnable saved) {
        run(() -> {
            operation.run();
            return service.recurring();
        }, updated -> {
            payments.set(updated);
            saved.run();
        }, paymentsBusy, paymentsError);
    }

    private void updateGoals(Write operation, Runnable saved) {
        run(() -> {
            operation.run();
            return service.goals();
        }, updated -> {
            goals.set(updated);
            saved.run();
        }, goalsBusy, goalsError);
    }

    private static <T> void run(Callable<T> work, Consumer<T> complete, SimpleBooleanProperty busy,
                                SimpleStringProperty error) {
        if (busy.get()) {
            return;
        }
        var task = new Task<T>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> {
            busy.set(false);
            complete.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            busy.set(false);
            Throwable cause = task.getException();
            error.set(cause instanceof ValidationException ? cause.getMessage()
                    : t("Something went wrong: {0}", cause.getMessage()));
        });
        busy.set(true);
        error.set("");
        Thread.ofVirtual().name("jeib-planning").start(task);
    }

    @FunctionalInterface
    private interface Write {
        void run() throws SQLException;
    }
}
