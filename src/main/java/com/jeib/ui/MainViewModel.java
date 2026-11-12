package com.jeib.ui;

import com.jeib.db.Database;
import com.jeib.model.Category;
import com.jeib.repository.CategoryRepository;
import com.jeib.service.CurrencyService;
import com.jeib.service.PreferencesService;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
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
    private final PreferencesService preferences;
    private final Clock clock;

    private final ObservableList<Category> categoryList = FXCollections.observableArrayList();
    private final StringProperty status = new SimpleStringProperty("Opening the database…");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty ready = new SimpleBooleanProperty(false);
    private final BooleanProperty setupNeeded = new SimpleBooleanProperty(false);
    private final ObjectProperty<ThemeMode> themeMode = new SimpleObjectProperty<>(ThemeMode.LIGHT);
    /** The theme actually shown: the mode, resolved by the clock when it's Automatic. */
    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.LIGHT);
    private final ObjectProperty<YearMonth> month;
    private final StringBinding monthLabel;

    /** @param clock decides which month the app opens on */
    public MainViewModel(Database database, CategoryRepository categories, CurrencyService currencies,
                         PreferencesService preferences, Clock clock) {
        this.preferences = preferences;
        this.clock = clock;
        this.database = database;
        this.categories = categories;
        this.currencies = currencies;
        this.month = new SimpleObjectProperty<>(YearMonth.now(clock));
        this.monthLabel = Bindings.createStringBinding(() -> DateTimeFormatter.ofPattern("MMMM yyyy", com.jeib.i18n.Messages.locale()).format(month.get()), month);
    }

    private record Loaded(List<Category> categories, boolean hasMainCurrency, String theme) {
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
                return new Loaded(categories.findActive(), currencies.mainCurrency().isPresent(),
                        preferences.theme().orElse(null));
            }
        };
        task.setOnSucceeded(event -> {
            categoryList.setAll(task.getValue().categories());
            themeMode.set(ThemeMode.parse(task.getValue().theme()));
            refreshTheme();
            setupNeeded.set(!task.getValue().hasMainCurrency());
            status.set("Database ready · " + categoryList.size() + " categories · " + database.file());
            loading.set(false);
            ready.set(true);
        });
        task.setOnFailed(event -> {
            status.set("Couldn't open the database: " + task.getException().getMessage());
            loading.set(false);
        });
        loading.set(true);
        Thread.ofVirtual().name("jeib-load").start(task);
    }

    /** Called when the first-launch setup has saved the main currency. */
    public void setupDone() {
        setupNeeded.set(false);
    }

    public void previousMonth() {
        month.set(month.get().minusMonths(1));
    }

    public void nextMonth() {
        month.set(month.get().plusMonths(1));
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

    /** True when no main currency has been chosen yet: the first-launch setup must run. */
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
