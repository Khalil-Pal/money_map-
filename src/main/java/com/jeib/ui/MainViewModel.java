package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.db.Database;
import com.jeib.i18n.Messages;
import com.jeib.model.Category;
import com.jeib.model.DateRange;
import com.jeib.repository.BudgetPlanRepository;
import com.jeib.repository.CategoryRepository;
import com.jeib.service.CurrencyService;
import com.jeib.service.PreferencesService;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
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
 * State for the main window: database status, theme, and the month shown by every page (the top
 * bar's ◀ month ▶ switcher). Database work runs on a background thread, never the JavaFX thread.
 */
public final class MainViewModel {


    private final Database database;
    private final CategoryRepository categories;
    private final CurrencyService currencies;
    private final BudgetPlanRepository budgetPlans;
    private final PreferencesService preferences;
    private final Clock clock;

    private final ObservableList<Category> categoryList = FXCollections.observableArrayList();
    private final StringProperty status = new SimpleStringProperty(t("Opening the database…"));
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty ready = new SimpleBooleanProperty(false);
    private final BooleanProperty setupNeeded = new SimpleBooleanProperty(false);
    private boolean currencySetupNeeded;
    private final ObjectProperty<ThemeMode> themeMode = new SimpleObjectProperty<>(ThemeMode.LIGHT);
    /** The theme actually shown: the mode, resolved by the clock when it's Automatic. */
    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.LIGHT);
    private final ObjectProperty<YearMonth> month;
    private final ObjectProperty<DateRange> customRange = new SimpleObjectProperty<>();
    private final StringBinding monthLabel;

    /** @param clock decides which month the app opens on */
    public MainViewModel(Database database, CategoryRepository categories, CurrencyService currencies,
                         BudgetPlanRepository budgetPlans, PreferencesService preferences, Clock clock) {
        this.preferences = preferences;
        this.clock = clock;
        this.database = database;
        this.categories = categories;
        this.currencies = currencies;
        this.budgetPlans = budgetPlans;
        this.month = new SimpleObjectProperty<>(YearMonth.now(clock));
        this.monthLabel = Bindings.createStringBinding(
                () -> DateTimeFormatter.ofPattern("LLLL yyyy", Messages.locale()).format(month.get()), month);
    }

    private record Loaded(List<Category> categories, boolean currencySetupNeeded, boolean firstPlanNeeded, String theme) {
    }

    /**
     * Migrates the schema, loads categories, and checks whether the first-launch setup is done, in
     * the background; {@link #readyProperty} turns true after.
     */
    public void load() {
        Task<Loaded> task = new Task<>() {
            @Override
            protected Loaded call() throws Exception {
                database.migrate();
                boolean needsCurrency = currencies.mainCurrency().isEmpty();
                boolean needsPlan = !needsCurrency && currencies.requiresFirstPlan() && !budgetPlans.hasCreatedPlan();
                return new Loaded(categories.findActive(), needsCurrency, needsPlan,
                        preferences.theme().orElse(null));
            }
        };
        task.setOnSucceeded(event -> {
            categoryList.setAll(task.getValue().categories());
            themeMode.set(ThemeMode.parse(task.getValue().theme()));
            refreshTheme();
            currencySetupNeeded = task.getValue().currencySetupNeeded();
            setupNeeded.set(currencySetupNeeded || task.getValue().firstPlanNeeded());
            status.set(t("Database ready · {0} categories · {1}", categoryList.size(),
                    Messages.ltr(database.file().toString())));
            loading.set(false);
            ready.set(true);
        });
        task.setOnFailed(event -> {
            status.set(t("Couldn't open the database: {0}", task.getException().getMessage()));
            loading.set(false);
        });
        loading.set(true);
        Thread.ofVirtual().name("jeib-load").start(task);
    }

    /** Called when the first-launch setup has saved the first plan. */
    public void setupDone() {
        currencySetupNeeded = false;
        setupNeeded.set(false);
    }

    boolean currencySetupNeeded() {
        return currencySetupNeeded;
    }

    public void previousMonth() {
        month.set(month.get().minusMonths(1));
    }

    public void nextMonth() {
        month.set(month.get().plusMonths(1));
    }

    public DateRange selectedDates() {
        DateRange range = customRange.get();
        return range == null ? DateRange.ofMonth(month.get()) : range;
    }

    /** Null returns Dashboard and Transactions to the selected calendar month. */
    public void setCustomRange(DateRange range) {
        customRange.set(range);
    }

    public ReadOnlyObjectProperty<DateRange> customRangeProperty() {
        return customRange;
    }

    /** The theme button: Light → Dark → Automatic → Light. */
    public void toggleTheme() {
        setThemeMode(themeMode.get().next());
    }

    /**
     * Re-checks the clock: in Automatic mode the theme turns dark at 7 PM and light at 7 AM while
     * the app is open. MainController calls this every minute.
     */
    public void refreshTheme() {
        theme.set(themeMode.get().resolve(java.time.LocalTime.now(clock)));
    }

    /** Switches the mode and remembers it for the next launch (saved in the background). */
    public void setThemeMode(ThemeMode chosen) {
        themeMode.set(chosen);
        refreshTheme();
        Task<Void> save = new Task<>() {
            @Override
            protected Void call() throws Exception {
                preferences.setTheme(chosen.name());
                return null;
            }
        };
        Thread.ofVirtual().name("jeib-theme").start(save);
    }

    public ObservableList<Category> categories() {
        return categoryList;
    }

    public ReadOnlyStringProperty statusProperty() {
        return status;
    }

    public ReadOnlyBooleanProperty loadingProperty() {
        return loading;
    }

    /** True once the schema is migrated, so pages may start reading the database. */
    public ReadOnlyBooleanProperty readyProperty() {
        return ready;
    }

    /** True while the first-launch currency or plan still needs to be saved. */
    public ReadOnlyBooleanProperty setupNeededProperty() {
        return setupNeeded;
    }

    /** The theme shown right now (Light or Dark). */
    public ReadOnlyObjectProperty<Theme> themeProperty() {
        return theme;
    }

    /** What the user chose: Light, Dark, or Automatic. */
    public ReadOnlyObjectProperty<ThemeMode> themeModeProperty() {
        return themeMode;
    }

    public ReadOnlyObjectProperty<YearMonth> monthProperty() {
        return month;
    }

    /** "November 2026" */
    public StringBinding monthLabel() {
        return monthLabel;
    }
}
