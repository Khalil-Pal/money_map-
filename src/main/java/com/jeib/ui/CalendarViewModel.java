package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.DateRange;
import com.jeib.repository.SettingsRepository;
import com.jeib.repository.TransactionRepository;
import com.jeib.service.CategoryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;

public final class CalendarViewModel {

    private static final String FIRST_MONTH = "calendar.first_month";

    private record Overview(YearMonth firstMonth, List<TransactionRow> todayRows) { }

    final ObjectProperty<YearMonth> firstMonth = new SimpleObjectProperty<>();
    final ObjectProperty<LocalDate> today = new SimpleObjectProperty<>();
    final ObjectProperty<List<TransactionRow>> todayRows = new SimpleObjectProperty<>();
    final ObjectProperty<String> todayError = new SimpleObjectProperty<>("");
    final ObjectProperty<Map<LocalDate, List<TransactionRow>>> days = new SimpleObjectProperty<>();
    final ObjectProperty<String> calendarError = new SimpleObjectProperty<>("");

    private final SettingsRepository settings;
    private final TransactionRepository transactions;
    private final CategoryService categories;
    private final Clock clock;
    private DateRange visibleDates;
    private boolean initialized;
    private Task<Overview> pendingOverview;
    private Task<Map<LocalDate, List<TransactionRow>>> pendingDays;

    public CalendarViewModel(SettingsRepository settings, TransactionRepository transactions,
                             CategoryService categories, Clock clock) {
        this.settings = settings;
        this.transactions = transactions;
        this.categories = categories;
        this.clock = clock;
        today.set(LocalDate.now(clock));
    }

    public void initialize() {
        initialized = true;
        reload();
    }

    void refreshToday() {
        LocalDate current = LocalDate.now(clock);
        if (!current.equals(today.get())) {
            today.set(current);
            todayRows.set(null);
            if (initialized) {
                loadOverview();
            }
        }
    }

    void showRange(DateRange range) {
        if (!Objects.equals(visibleDates, range)) {
            visibleDates = range;
            if (initialized) {
                loadDays();
            }
        }
    }

    ObservableValue<List<TransactionRow>> rowsOn(LocalDate date) {
        return days.map(entries -> entries.getOrDefault(date, List.of()));
    }

    public void reload() {
        if (initialized) {
            LocalDate current = LocalDate.now(clock);
            if (!current.equals(today.get())) {
                today.set(current);
                todayRows.set(null);
            }
            loadOverview();
            loadDays();
        }
    }

    private void loadOverview() {
        if (pendingOverview != null) {
            pendingOverview.cancel();
        }
        LocalDate date = today.get();
        todayError.set("");
        var task = new Task<Overview>() {
            @Override
            protected Overview call() throws Exception {
                var saved = settings.get(FIRST_MONTH).map(YearMonth::parse);
                YearMonth first = saved.orElse(YearMonth.from(date));
                YearMonth earliest = transactions.firstRecordedMonth().filter(candidate -> candidate.isBefore(first))
                        .orElse(first);
                if (saved.isEmpty()) {
                    settings.save(Map.of(FIRST_MONTH, earliest.toString()));
                }
                var rows = TransactionRow.from(transactions.findByRange(new DateRange(date, date)), categories.list());
                return new Overview(earliest, rows);
            }
        };
        pendingOverview = task;
        task.setOnSucceeded(event -> {
            if (pendingOverview == task) {
                firstMonth.set(task.getValue().firstMonth());
                todayRows.set(task.getValue().todayRows());
            }
        });
        task.setOnFailed(event -> {
            if (pendingOverview == task) {
                todayError.set(t("Couldn't load today's operations: {0}", task.getException().getMessage()));
            }
        });
        Thread.ofVirtual().name("jeib-calendar-overview").start(task);
    }

    private void loadDays() {
        if (pendingDays != null) {
            pendingDays.cancel();
            pendingDays = null;
        }
        calendarError.set("");
        days.set(null);
        DateRange dates = visibleDates;
        if (dates == null) {
            return;
        }
        var task = new Task<Map<LocalDate, List<TransactionRow>>>() {
            @Override
            protected Map<LocalDate, List<TransactionRow>> call() throws Exception {
                return TransactionRow.from(transactions.findByRange(dates), categories.list()).stream()
                        .collect(Collectors.groupingBy(row -> row.source().occurredOn()));
            }
        };
        pendingDays = task;
        task.setOnSucceeded(event -> {
            if (pendingDays == task) {
                days.set(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            if (pendingDays == task) {
                calendarError.set(t("Couldn't load the calendar: {0}", task.getException().getMessage()));
            }
        });
        Thread.ofVirtual().name("jeib-calendar-days").start(task);
    }
}
