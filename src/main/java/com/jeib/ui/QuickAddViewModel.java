package com.jeib.ui;

import com.jeib.model.Category;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.TransactionType;
import com.jeib.service.CategoryService;
import com.jeib.service.CurrencyService;
import com.jeib.service.QuickAddParser;
import com.jeib.service.TransactionCommands;
import com.jeib.service.ValidationException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;

/**
 * The quick-add bar: type "250 food lunch", see what will be saved, press Enter. Parsing is
 * {@link QuickAddParser}'s job; saving goes through the same undoable commands as the dialog.
 */
public final class QuickAddViewModel {

    private final TransactionCommands commands;
    private final CategoryService categories;
    private final CurrencyService currencies;
    private final Clock clock;
    private final Runnable afterAdd;

    private final StringProperty text = new SimpleStringProperty("");
    private final StringProperty preview = new SimpleStringProperty("");
    private final BooleanProperty previewIsError = new SimpleBooleanProperty(false);
    private final BooleanProperty saving = new SimpleBooleanProperty(false);

    private List<Category> knownCategories = List.of();
    private SupportedCurrency mainCurrency;

    /** @param afterAdd runs on the JavaFX thread after a successful add, e.g. reload the list */
    public QuickAddViewModel(TransactionCommands commands, CategoryService categories, CurrencyService currencies,
                             Clock clock, Runnable afterAdd) {
        this.commands = commands;
        this.categories = categories;
        this.currencies = currencies;
        this.clock = clock;
        this.afterAdd = afterAdd;
        text.addListener((obs, old, now) -> updatePreview());
    }

    // ---- loading what the parser needs --------------------------------------

    record Data(List<Category> categories, Optional<SupportedCurrency> mainCurrency) {
    }

    /** Database work for {@link #load}; tests call it directly. */
    Data loadData() throws SQLException {
        return new Data(categories.list(), currencies.mainCurrency());
    }

    void apply(Data data) {
        knownCategories = data.categories();
        mainCurrency = data.mainCurrency().orElse(null);
        updatePreview();
    }

    /** Refreshes categories and the main currency in the background (cheap; done on focus). */
    public void load() {
        Task<Data> task = new Task<>() {
            @Override
            protected Data call() throws Exception {
                return loadData();
            }
        };
        task.setOnSucceeded(event -> apply(task.getValue()));
        Thread.ofVirtual().name("jeib-quick-add-load").start(task);
    }

    // ---- preview ----------------------------------------------------------

    /** Parses the current text; throws a ValidationException for text that can't be added. */
    QuickAddParser.Parsed parseNow() {
        if (mainCurrency == null) {
            throw new ValidationException(QuickAddParser.FIELD, "Choose your main currency first.");
        }
        return QuickAddParser.parse(text.get(), knownCategories, mainCurrency, LocalDate.now(clock));
    }

    private void updatePreview() {
        if (text.get().isBlank() || knownCategories.isEmpty()) {
            preview.set("");
            previewIsError.set(false);
            return;
        }
        try {
            preview.set(describe(parseNow()) + "   ·   Enter to add, Esc to clear");
            previewIsError.set(false);
        } catch (ValidationException e) {
            preview.set(e.getMessage());
            previewIsError.set(true);
        }
    }

    /** "Expense · 250.00 ₽ · Food · “lunch”" */
    static String describe(QuickAddParser.Parsed parsed) {
        boolean income = parsed.input().type() == TransactionType.INCOME;
        StringBuilder line = new StringBuilder(income ? "Income" : "Expense")
                .append(" · ").append(income ? "+" : "")
                .append(parsed.input().currency().format(parsed.amountMinor()))
                .append(" · ").append(parsed.category().name());
        if (!parsed.categoryMatched()) {
            line.append(" (no category matched)");
        }
        if (parsed.input().note() != null) {
            line.append(" · “").append(parsed.input().note()).append("”");
        }
        return line.toString();
    }

    // ---- adding -------------------------------------------------------------

    /** Database work for {@link #submit}; tests call it directly. */
    void addNow(QuickAddParser.Parsed parsed) throws SQLException {
        commands.add(parsed.input());
    }

    /**
     * Adds the parsed line in the background. On success the field is cleared, the list reloads,
     * and {@code onAdded} gets a message for the user ("Added 250.00 ₽ · Food.").
     */
    public void submit(Consumer<String> onAdded) {
        if (saving.get()) {
            return;
        }
        QuickAddParser.Parsed parsed;
        try {
            parsed = parseNow();
        } catch (ValidationException e) {
            preview.set(e.getMessage());
            previewIsError.set(true);
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                addNow(parsed);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            saving.set(false);
            text.set("");
            afterAdd.run();
            onAdded.accept("Added " + (parsed.input().type() == TransactionType.INCOME ? "+" : "")
                    + parsed.input().currency().format(parsed.amountMinor()) + " · " + parsed.category().name() + ".");
        });
        task.setOnFailed(event -> {
            saving.set(false);
            Throwable error = task.getException();
            String message = error instanceof ValidationException ? error.getMessage()
                    : "Something went wrong: " + error.getMessage();
            if (error instanceof ValidationException v && "currency".equals(v.field())) {
                message += " Use New transaction to enter the rate.";
            }
            preview.set(message);
            previewIsError.set(true);
        });
        saving.set(true);
        Thread.ofVirtual().name("jeib-quick-add").start(task);
    }

    public void clear() {
        text.set("");
    }

    public StringProperty textProperty() {
        return text;
    }

    /** What Enter will save, or why it can't; empty while the field is empty. */
    public ReadOnlyStringProperty previewProperty() {
        return preview;
    }

    public ReadOnlyBooleanProperty previewIsErrorProperty() {
        return previewIsError;
    }
}
