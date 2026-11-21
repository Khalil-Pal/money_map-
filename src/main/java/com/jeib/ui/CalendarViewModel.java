package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.DateRange;
import com.jeib.repository.SettingsRepository;
import com.jeib.repository.TransactionRepository;
import com.jeib.service.BudgetService;
import com.jeib.service.CategoryService;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;

public final class CalendarViewModel {

    private static final String FIRST_MONTH = "calendar.first_month";

    record History(BudgetService.PeriodSpending spending, List<TransactionRow> rows) { }

    final ObjectProperty<YearMonth> firstMonth = new SimpleObjectProperty<>();
    final ObjectProperty<YearMonth> month = new SimpleObjectProperty<>();
    final ObjectProperty<DateRange> customRange = new SimpleObjectProperty<>();
    final ObjectProperty<History> history = new SimpleObjectProperty<>();
    final ObjectProperty<String> message = new SimpleObjectProperty<>("");

    private final SettingsRepository settings;
    private final TransactionRepository transactions;
    private final BudgetService budgets;
    private final CategoryService categories;
    private final Clock clock;
    private Task<History> pending;

    public CalendarViewModel(SettingsRepository settings, TransactionRepository transactions, BudgetService budgets,
                             CategoryService categories, Clock clock) {
        this.settings = settings;
        this.transactions = transactions;
        this.budgets = budgets;
        this.categories = categories;
        this.clock = clock;
        month.addListener((obs, before, after) -> {
            if (customRange.get() == null) {
                periodChanged();
            }
        });
        customRange.addListener((obs, before, after) -> periodChanged());
    }

    public void initialize() {
        var task = new Task<YearMonth>() {
            @Override
            protected YearMonth call() throws Exception {
                var saved = settings.get(FIRST_MONTH).map(YearMonth::parse);
                if (saved.isPresent()) {
                    return saved.get();
                }
                YearMonth today = currentMonth();
                YearMonth first = transactions.firstRecordedMonth().filter(candidate -> candidate.isBefore(today))
                        .orElse(today);
                settings.save(Map.of(FIRST_MONTH, first.toString()));
                return first;
            }
        };
        task.setOnSucceeded(event -> {
            firstMonth.set(task.getValue());
            month.set(task.getValue());
        });
        task.setOnFailed(event -> showError(task));
        Thread.ofVirtual().start(task);
    }

    YearMonth currentMonth() {
        return YearMonth.now(clock);
    }

    DateRange selectedDates() {
        return customRange.get() == null ? DateRange.ofMonth(month.get()) : customRange.get();
    }

    void selectMonth(YearMonth chosen) {
        month.set(chosen);
        customRange.set(null);
    }

    private void periodChanged() {
        history.set(null);
        message.set("");
        reload();
    }

    public void reload() {
        if (month.get() == null) {
            return;
        }
        DateRange dates = selectedDates();
        if (pending != null) {
            pending.cancel();
        }
        var task = new Task<History>() {
            @Override
            protected History call() throws Exception {
                return new History(budgets.spendingBetween(dates),
                        TransactionRow.from(transactions.findByRange(dates), categories.list()));
            }
        };
        pending = task;
        task.setOnSucceeded(event -> {
            message.set("");
            history.set(task.getValue());
        });
        task.setOnFailed(event -> showError(task));
        Thread.ofVirtual().start(task);
    }

    private void showError(Task<?> task) {
        message.set(t("Couldn't load spending: {0}", task.getException().getMessage()));
    }
}
