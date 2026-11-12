package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.Category;
import com.jeib.service.CategoryService;
import com.jeib.service.TransactionCommands;
import com.jeib.service.TransactionService;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

/**
 * State for the Transactions page: the selected month's transactions, newest first. Reloads by
 * itself when the month changes (top bar ◀ ▶) or when the database becomes ready. Can be narrowed
 * to one category (the Dashboard's drill-downs).
 */
public final class TransactionsViewModel {

    /** "November 2026", in the app's language. */
    private static DateTimeFormatter month() {
        return DateTimeFormatter.ofPattern("MMMM yyyy", com.jeib.i18n.Messages.locale());
    }

    private final TransactionService transactions;
    private final TransactionCommands commands;
    private final Runnable afterChange;
    private final CategoryService categories;
    private final ReadOnlyObjectProperty<YearMonth> month;

    private final ObservableList<TransactionRow> rows = FXCollections.observableArrayList();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final StringProperty message = new SimpleStringProperty("");
    private final StringProperty summary = new SimpleStringProperty("");
    private final ObjectProperty<Category> filter = new SimpleObjectProperty<>();

    /**
     * @param month the month chosen in the top bar (shared with the other pages)
     * @param ready true once the database is migrated; nothing loads before that
     */
    public TransactionsViewModel(TransactionService transactions, TransactionCommands commands,
                                 CategoryService categories,
                                 ReadOnlyObjectProperty<YearMonth> month, ReadOnlyBooleanProperty ready,
                                 Runnable afterChange) {
        this.transactions = transactions;
        this.commands = commands;
        this.afterChange = afterChange;
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

    /** Shows only {@code category}'s transactions, or all of them for null. */
    public void filterBy(Category category) {
        if (Objects.equals(filter.get(), category)) {
            return;
        }
        filter.set(category);
        load();
    }

    /** Loads the current month in the background. */
    public void load() {
        YearMonth requested = month.get();
        Category only = filter.get();
        Task<List<TransactionRow>> task = new Task<>() {
            @Override
            protected List<TransactionRow> call() throws Exception {
                var list = transactions.listMonth(requested);
                if (only != null) {
                    list = list.stream().filter(t -> t.categoryId().equals(only.id())).toList();
                }
                return TransactionRow.from(list, categories.list());
            }
        };
        task.setOnSucceeded(event -> {
            // Clicking ◀ ▶ quickly starts several loads; only the one for the month on screen counts.
            if (!requested.equals(month.get()) || !Objects.equals(only, filter.get())) {
                return;
            }
            rows.setAll(task.getValue());
            int count = rows.size();
            String monthName = month().format(requested);
            if (only == null) {
                summary.set(count == 0 ? "" : count == 1 ? t("1 transaction") : t("{0} transactions", count));
                message.set(count == 0 ? t("No transactions in {0}.", monthName) : "");
            } else {
                summary.set(count == 0 ? "" : count == 1 ? t("1 transaction in {0}", only.name())
                        : t("{0} transactions in {1}", count, only.name()));
                message.set(count == 0 ? t("No {0} transactions in {1}.", only.name(), monthName) : "");
            }
            loading.set(false);
        });
        task.setOnFailed(event -> {
            if (!requested.equals(month.get())) {
                return;
            }
            rows.clear();
            summary.set("");
            message.set(t("Couldn't load transactions: {0}", task.getException().getMessage()));
            loading.set(false);
        });
        loading.set(true);
        Thread.ofVirtual().name("jeib-transactions").start(task);
    }

    /** Deletes (undoably) in the background, reloads, then runs {@code onDeleted} (JavaFX thread). */
    public void delete(String id, Runnable onDeleted) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                commands.delete(id);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            afterChange.run(); // reloads this list too
            onDeleted.run();
        });
        task.setOnFailed(event -> {
            message.set(t("Couldn't delete: {0}", task.getException().getMessage()));
            load();
        });
        Thread.ofVirtual().name("jeib-delete").start(task);
    }

    public ObservableList<TransactionRow> rows() {
        return rows;
    }

    /** The category the list is narrowed to, or null for all. */
    public ReadOnlyObjectProperty<Category> filterProperty() {
        return filter;
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
