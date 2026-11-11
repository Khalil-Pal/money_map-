package com.jeib.ui;

import com.jeib.db.Database;
import com.jeib.model.Category;
import com.jeib.repository.CategoryRepository;
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

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    private final Database database;
    private final CategoryRepository categories;

    private final ObservableList<Category> categoryList = FXCollections.observableArrayList();
    private final StringProperty status = new SimpleStringProperty("Opening the database…");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty ready = new SimpleBooleanProperty(false);
    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.LIGHT);
    private final ObjectProperty<YearMonth> month;
    private final StringBinding monthLabel;

    /** @param clock decides which month the app opens on */
    public MainViewModel(Database database, CategoryRepository categories, Clock clock) {
        this.database = database;
        this.categories = categories;
        this.month = new SimpleObjectProperty<>(YearMonth.now(clock));
        this.monthLabel = Bindings.createStringBinding(() -> MONTH_LABEL.format(month.get()), month);
    }

    /** Migrates the schema and loads categories in the background; {@link #readyProperty} turns true after. */
    public void load() {
        Task<List<Category>> task = new Task<>() {
            @Override
            protected List<Category> call() throws Exception {
                database.migrate();
                return categories.findActive();
            }
        };
        task.setOnSucceeded(event -> {
            categoryList.setAll(task.getValue());
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

    public void previousMonth() {
        month.set(month.get().minusMonths(1));
    }

    public void nextMonth() {
        month.set(month.get().plusMonths(1));
    }

    public void toggleTheme() {
        theme.set(theme.get().toggled());
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

    public ObjectProperty<Theme> themeProperty() {
        return theme;
    }

    public ReadOnlyObjectProperty<YearMonth> monthProperty() {
        return month;
    }

    /** "November 2026" */
    public StringBinding monthLabel() {
        return monthLabel;
    }
}
