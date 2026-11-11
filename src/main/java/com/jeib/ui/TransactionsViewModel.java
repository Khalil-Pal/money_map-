package com.jeib.ui;

import com.jeib.service.CategoryService;
import com.jeib.service.TransactionService;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

/**
 * State for the Transactions page: the selected month's transactions, newest first. Reloads by
 * itself when the month changes (top bar ◀ ▶) or when the database becomes ready.
 */
public final class TransactionsViewModel {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    private final TransactionService transactions;
    private final CategoryService categories;
    private final ReadOnlyObjectProperty<YearMonth> month;

    private final ObservableList<TransactionRow> rows = FXCollections.observableArrayList();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final StringProperty message = new SimpleStringProperty("");
    private final StringProperty summary = new SimpleStringProperty("");

    /**
     * @param month the month chosen in the top bar (shared with the other pages)
     * @param ready true once the database is migrated; nothing loads before that
     */
    public TransactionsViewModel(TransactionService transactions, CategoryService categories,
                                 ReadOnlyObjectProperty<YearMonth> month, ReadOnlyBooleanProperty ready) {
        this.transactions = transactions;
        this.categories = categories;
        this.month = month;
        month.addListener((obs, oldMonth, newMonth) -> {
            if (ready.get()) {
                load();
            }
        });
        ready.addListener((obs, wasReady, isReady) -> {
            if (isReady) {
                load();
            }
        });
    }

    /** Loads the current month in the background. */
    public void load() {
        YearMonth requested = month.get();
        Task<List<TransactionRow>> task = new Task<>() {
            @Override
            protected List<TransactionRow> call() throws Exception {
                return TransactionRow.from(transactions.listMonth(requested), categories.list());
            }
        };
        task.setOnSucceeded(event -> {
            // Clicking ◀ ▶ quickly starts several loads; only the one for the month on screen counts.
            if (!requested.equals(month.get())) {
                return;
            }
            rows.setAll(task.getValue());
            int count = rows.size();
            summary.set(count == 0 ? "" : count == 1 ? "1 transaction" : count + " transactions");
            message.set(count == 0 ? "No transactions in " + MONTH.format(requested) + "." : "");
            loading.set(false);
        });
        task.setOnFailed(event -> {
            if (!requested.equals(month.get())) {
                return;
            }
            rows.clear();
            summary.set("");
            message.set("Couldn't load transactions: " + task.getException().getMessage());
            loading.set(false);
        });
        loading.set(true);
        Thread.ofVirtual().name("jeib-transactions").start(task);
    }

    public ObservableList<TransactionRow> rows() {
        return rows;
    }

    public ReadOnlyBooleanProperty loadingProperty() {
        return loading;
    }

    /** Shown instead of the list: the empty-month text or an error; empty when there are rows. */
    public ReadOnlyStringProperty messageProperty() {
        return message;
    }

    /** "12 transactions" */
    public ReadOnlyStringProperty summaryProperty() {
        return summary;
    }
}
