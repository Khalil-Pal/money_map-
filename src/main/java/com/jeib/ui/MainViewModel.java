package com.jeib.ui;

import com.jeib.db.Database;
import com.jeib.model.Category;
import com.jeib.repository.CategoryRepository;
import java.util.List;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

/** State for the main window. Database work runs on a background thread, never the JavaFX thread. */
public final class MainViewModel {

    private final Database database;
    private final CategoryRepository categories;

    private final ObservableList<Category> categoryList = FXCollections.observableArrayList();
    private final StringProperty status = new SimpleStringProperty("Opening the database…");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.LIGHT);

    public MainViewModel(Database database, CategoryRepository categories) {
        this.database = database;
        this.categories = categories;
    }

    /** Migrates the schema and loads categories in the background. */
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
        });
        task.setOnFailed(event -> {
            status.set("Couldn't open the database: " + task.getException().getMessage());
            loading.set(false);
        });
        loading.set(true);
        Thread.ofVirtual().name("jeib-load").start(task);
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

    public ObjectProperty<Theme> themeProperty() {
        return theme;
    }
}
