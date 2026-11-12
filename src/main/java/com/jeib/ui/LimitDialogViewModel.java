package com.jeib.ui;

import com.jeib.model.SupportedCurrency;
import com.jeib.service.BudgetService;
import com.jeib.service.BudgetService.LimitLine;
import com.jeib.service.ValidationException;
import java.math.BigDecimal;
import java.sql.SQLException;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;

/** The small dialog that sets, changes, or removes one limit (a category's or the overall one). */
public final class LimitDialogViewModel {

    @FunctionalInterface
    interface Work {
        void run() throws SQLException;
    }

    private final BudgetService budgets;
    private final String categoryId; // null = overall
    private final boolean hasLimit;

    private final String title;
    private final String hint;
    private final StringProperty amount = new SimpleStringProperty("");
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanProperty saving = new SimpleBooleanProperty(false);

    public LimitDialogViewModel(BudgetService budgets, LimitLine line, SupportedCurrency main) {
        this.budgets = budgets;
        this.categoryId = line.isOverall() ? null : line.category().id();
        this.hasLimit = line.limitMinor() != null;
        this.title = line.isOverall() ? "Monthly limit" : line.category().name() + " limit";
        this.hint = (line.isOverall() ? "The most you want to spend in a month, across all categories."
                : "The most you want to spend on " + line.category().name() + " in a month.")
                + " In " + main + "; applies to every month.";
        if (hasLimit) {
            amount.set(BigDecimal.valueOf(line.limitMinor(), 2).stripTrailingZeros().toPlainString());
        }
        amount.addListener((obs, old, now) -> error.set(""));
    }

    /** Database work for {@link #save}; tests call it directly. */
    void saveNow(String text) throws SQLException {
        budgets.setLimit(categoryId, text);
    }

    /** Database work for {@link #remove}; tests call it directly. */
    void removeNow() throws SQLException {
        budgets.removeLimit(categoryId);
    }

    public void save(Runnable onDone) {
        String text = amount.get();
        run(() -> saveNow(text), onDone);
    }

    public void remove(Runnable onDone) {
        run(this::removeNow, onDone);
    }

    private void run(Work work, Runnable onDone) {
        if (saving.get()) {
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                work.run();
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            saving.set(false);
            onDone.run();
        });
        task.setOnFailed(event -> {
            saving.set(false);
            Throwable e = task.getException();
            error.set(e instanceof ValidationException ? e.getMessage() : "Something went wrong: " + e.getMessage());
        });
        error.set("");
        saving.set(true);
        Thread.ofVirtual().name("jeib-limit").start(task);
    }

    public String title() {
        return title;
    }

    public String hint() {
        return hint;
    }

    /** Whether a limit exists (the dialog then offers "Remove limit"). */
    public boolean hasLimit() {
        return hasLimit;
    }

    public StringProperty amountProperty() {
        return amount;
    }

    public ReadOnlyStringProperty errorProperty() {
        return error;
    }

    public ReadOnlyBooleanProperty savingProperty() {
        return saving;
    }
}
