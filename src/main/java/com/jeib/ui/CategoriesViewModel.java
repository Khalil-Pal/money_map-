package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.Category;
import com.jeib.model.TransactionType;
import com.jeib.service.CategoryService;
import java.util.List;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

/** State for the Categories page: the live expense and income categories, in palette order. */
public final class CategoriesViewModel {

    private final CategoryService categories;

    private final ObservableList<Category> expense = FXCollections.observableArrayList();
    private final ObservableList<Category> income = FXCollections.observableArrayList();
    private final StringProperty message = new SimpleStringProperty("");

    public CategoriesViewModel(CategoryService categories, ReadOnlyBooleanProperty ready) {
        this.categories = categories;
        ready.addListener((obs, was, isReady) -> {
            if (isReady) {
                load();
            }
        });
    }

    public void load() {
        Task<List<Category>> task = new Task<>() {
            @Override
            protected List<Category> call() throws Exception {
                return categories.list();
            }
        };
        task.setOnSucceeded(event -> {
            expense.setAll(task.getValue().stream().filter(c -> c.kind() == TransactionType.EXPENSE).toList());
            income.setAll(task.getValue().stream().filter(c -> c.kind() == TransactionType.INCOME).toList());
            message.set("");
        });
        task.setOnFailed(event -> message.set(t("Couldn't load categories: {0}", task.getException().getMessage())));
        Thread.ofVirtual().name("jeib-categories").start(task);
    }

    public ObservableList<Category> expense() {
        return expense;
    }

    public ObservableList<Category> income() {
        return income;
    }

    public ReadOnlyStringProperty messageProperty() {
        return message;
    }
}
