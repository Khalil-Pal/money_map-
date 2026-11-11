package com.jeib.ui;

import com.jeib.model.SupportedCurrency;
import com.jeib.service.CurrencyService;
import java.sql.SQLException;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;

/** First-launch setup: the user chooses their main currency before anything else. */
public final class SetupViewModel {

    private final CurrencyService currencies;

    private final ObjectProperty<SupportedCurrency> choice = new SimpleObjectProperty<>();
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanProperty saving = new SimpleBooleanProperty(false);
    private final BooleanBinding canSave = choice.isNotNull().and(saving.not());

    public SetupViewModel(CurrencyService currencies) {
        this.currencies = currencies;
    }

    /** The database work, run off the JavaFX thread by {@link #save}; tests call it directly. */
    void saveNow(SupportedCurrency currency) throws SQLException {
        currencies.setMainCurrency(currency);
    }

    /** Saves the chosen currency in the background, then runs {@code onDone} on the JavaFX thread. */
    public void save(Runnable onDone) {
        SupportedCurrency chosen = choice.get();
        if (chosen == null) {
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                saveNow(chosen);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            saving.set(false);
            onDone.run();
        });
        task.setOnFailed(event -> {
            saving.set(false);
            error.set("Couldn't save: " + task.getException().getMessage());
        });
        error.set("");
        saving.set(true);
        Thread.ofVirtual().name("jeib-setup").start(task);
    }

    public ObjectProperty<SupportedCurrency> choiceProperty() {
        return choice;
    }

    public ReadOnlyStringProperty errorProperty() {
        return error;
    }

    public BooleanBinding canSave() {
        return canSave;
    }
}
