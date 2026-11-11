package com.jeib.ui;

import com.jeib.model.Category;
import com.jeib.model.ExchangeRates;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.Transaction;
import com.jeib.model.TransactionType;
import com.jeib.service.CategoryService;
import com.jeib.service.CurrencyService;
import com.jeib.service.TransactionInput;
import com.jeib.service.TransactionService;
import com.jeib.service.ValidationException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
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
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;

/**
 * State for the New / Edit transaction dialog. The rules live in {@link TransactionService}; this
 * class only collects what the user typed, sends it, and shows each error next to its field.
 *
 * <p>When the chosen currency isn't the main one, the dialog also shows its exchange rate
 * ("1 USD = 92.50 RUB"), prefilled with the saved rate; a changed or new rate is saved first.
 */
public final class TransactionDialogViewModel {

    /** Error key for problems that aren't about one field. */
    public static final String FORM = "form";
    /** Every error key: the {@link ValidationException#field()} names plus {@link #FORM}. */
    public static final List<String> ERROR_KEYS =
            List.of("type", "amount", "currency", "rate", "date", "category", "note", FORM);

    private final TransactionService transactions;
    private final CategoryService categories;
    private final CurrencyService currencies;
    private final Clock clock;

    private String editingId; // null while adding
    private final StringProperty title = new SimpleStringProperty("New transaction");
    private final ObjectProperty<TransactionType> type = new SimpleObjectProperty<>(TransactionType.EXPENSE);
    private final StringProperty amount = new SimpleStringProperty("");
    private final ObjectProperty<SupportedCurrency> currency = new SimpleObjectProperty<>();
    private final StringProperty rate = new SimpleStringProperty("");
    private final ObjectProperty<LocalDate> date = new SimpleObjectProperty<>();
    private final StringProperty categoryId = new SimpleStringProperty();
    private final StringProperty note = new SimpleStringProperty("");

    private final ObjectProperty<SupportedCurrency> mainCurrency = new SimpleObjectProperty<>();
    private Map<SupportedCurrency, BigDecimal> savedRates = Map.of();
    private final ObservableList<Category> allCategories = FXCollections.observableArrayList();
    private final FilteredList<Category> categoriesForType = new FilteredList<>(allCategories);
    private final BooleanBinding rateNeeded = currency.isNotNull().and(mainCurrency.isNotNull())
            .and(Bindings.notEqual(currency, mainCurrency));
    private final StringBinding rateLabel = Bindings.createStringBinding(
            () -> currency.get() == null ? "" : "1 " + currency.get() + " =", currency);
    private final Map<String, StringProperty> errors = new LinkedHashMap<>();
    private final BooleanProperty saving = new SimpleBooleanProperty(false);

    public TransactionDialogViewModel(TransactionService transactions, CategoryService categories,
                                      CurrencyService currencies, Clock clock) {
        this.transactions = transactions;
        this.categories = categories;
        this.currencies = currencies;
        this.clock = clock;
        ERROR_KEYS.forEach(key -> errors.put(key, new SimpleStringProperty("")));

        type.addListener((obs, oldType, newType) -> {
            categoriesForType.setPredicate(c -> c.kind() == newType);
            // A Food expense switched to income can't stay in Food.
            if (allCategories.stream().noneMatch(c -> c.id().equals(categoryId.get()) && c.kind() == newType)) {
                categoryId.set(null);
            }
        });
        categoriesForType.setPredicate(c -> c.kind() == type.get());
        currency.addListener((obs, oldCurrency, newCurrency) -> rate.set(savedRateText(newCurrency)));

        clearErrorOnChange(type, "type");
        clearErrorOnChange(amount, "amount");
        clearErrorOnChange(currency, "currency", "rate");
        clearErrorOnChange(rate, "rate");
        clearErrorOnChange(date, "date");
        clearErrorOnChange(categoryId, "category");
        clearErrorOnChange(note, "note");
    }

    private void clearErrorOnChange(ObservableValue<?> value, String... keys) {
        value.addListener((obs, oldValue, newValue) -> {
            for (String key : keys) {
                errors.get(key).set("");
            }
        });
    }

    // ---- opening ---------------------------------------------------------

    public void startNew() {
        editingId = null;
        title.set("New transaction");
        type.set(TransactionType.EXPENSE);
        amount.set("");
        currency.set(mainCurrency.get()); // null until loaded; apply() fills it in
        date.set(LocalDate.now(clock));
        categoryId.set(null);
        note.set("");
    }

    public void startEdit(Transaction transaction) {
        editingId = transaction.id();
        title.set("Edit transaction");
        type.set(transaction.type());
        amount.set(BigDecimal.valueOf(transaction.amountMinor(), 2).toPlainString());
        currency.set(SupportedCurrency.valueOf(transaction.currency()));
        date.set(transaction.occurredOn());
        categoryId.set(transaction.categoryId());
        note.set(transaction.note() == null ? "" : transaction.note());
    }

    /** What the dialog needs from the database. */
    record Data(List<Category> categories, ExchangeRates rates) {
    }

    /** Database work for {@link #load}, off the JavaFX thread; tests call it directly. */
    Data loadData() throws SQLException {
        return new Data(categories.list(), currencies.rates());
    }

    /** Puts loaded data into the dialog (JavaFX thread). */
    void apply(Data data) {
        String keepCategory = categoryId.get();
        allCategories.setAll(data.categories());
        categoryId.set(keepCategory);
        mainCurrency.set(data.rates().main());
        savedRates = data.rates().rates();
        if (currency.get() == null) {
            currency.set(data.rates().main());
        }
        rate.set(savedRateText(currency.get()));
    }

    /** Loads categories and rates in the background. Call after startNew / startEdit. */
    public void load() {
        Task<Data> task = new Task<>() {
            @Override
            protected Data call() throws Exception {
                return loadData();
            }
        };
        task.setOnSucceeded(event -> apply(task.getValue()));
        task.setOnFailed(event -> errors.get(FORM).set(message(task.getException())));
        Thread.ofVirtual().name("jeib-dialog-load").start(task);
    }

    private String savedRateText(SupportedCurrency c) {
        BigDecimal saved = c == null ? null : savedRates.get(c);
        return saved == null ? "" : saved.stripTrailingZeros().toPlainString();
    }

    // ---- saving ----------------------------------------------------------

    /**
     * A snapshot of the form, taken on the JavaFX thread so the background save never reads
     * properties the user might still be changing.
     *
     * @param saveRate whether the rate was changed or is new and must be saved first
     */
    record SaveRequest(String editingId, TransactionInput input, boolean saveRate, String rateText) {
    }

    SaveRequest request() {
        TransactionInput input = new TransactionInput(type.get(), amount.get(), currency.get(), date.get(),
                categoryId.get(), note.get());
        // Save the rate when it's new (nothing saved yet, even if the field is empty: the service then
        // says what's missing) or when the user changed it.
        String saved = savedRateText(currency.get());
        boolean saveRate = rateNeeded.get() && (saved.isEmpty() || !rate.get().trim().equals(saved));
        return new SaveRequest(editingId, input, saveRate, rate.get());
    }

    /**
     * Saves the rate (if needed) and the transaction. Returns the errors to show, keyed like
     * {@link #ERROR_KEYS}; empty means it was saved.
     */
    Map<String, String> perform(SaveRequest request) throws SQLException {
        try {
            if (request.saveRate()) {
                currencies.setRate(request.input().currency(), request.rateText());
            }
            if (request.editingId() == null) {
                transactions.add(request.input());
            } else {
                transactions.edit(request.editingId(), request.input());
            }
            return Map.of();
        } catch (ValidationException e) {
            return Map.of(e.field() == null ? FORM : e.field(), e.getMessage());
        }
    }

    /** Saves in the background; on success runs {@code onSaved} (JavaFX thread), else shows the errors. */
    public void save(Runnable onSaved) {
        if (saving.get()) {
            return;
        }
        SaveRequest request = request();
        Task<Map<String, String>> task = new Task<>() {
            @Override
            protected Map<String, String> call() throws Exception {
                return perform(request);
            }
        };
        task.setOnSucceeded(event -> {
            saving.set(false);
            if (task.getValue().isEmpty()) {
                onSaved.run();
            } else {
                showErrors(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            saving.set(false);
            errors.get(FORM).set(message(task.getException()));
        });
        errors.values().forEach(error -> error.set(""));
        saving.set(true);
        Thread.ofVirtual().name("jeib-dialog-save").start(task);
    }

    void showErrors(Map<String, String> found) {
        found.forEach((key, text) -> errors.getOrDefault(key, errors.get(FORM)).set(text));
    }

    private static String message(Throwable error) {
        return "Something went wrong: " + error.getMessage();
    }

    // ---- properties for the controller -------------------------------------

    public ReadOnlyStringProperty titleProperty() {
        return title;
    }

    public ObjectProperty<TransactionType> typeProperty() {
        return type;
    }

    public StringProperty amountProperty() {
        return amount;
    }

    public ObjectProperty<SupportedCurrency> currencyProperty() {
        return currency;
    }

    public StringProperty rateProperty() {
        return rate;
    }

    public ObjectProperty<LocalDate> dateProperty() {
        return date;
    }

    public StringProperty categoryIdProperty() {
        return categoryId;
    }

    public StringProperty noteProperty() {
        return note;
    }

    public ReadOnlyObjectProperty<SupportedCurrency> mainCurrencyProperty() {
        return mainCurrency;
    }

    /** Only the categories that match the chosen type (expense or income). */
    public ObservableList<Category> categoriesForType() {
        return categoriesForType;
    }

    /** True when the chosen currency isn't the main one, so the rate row is shown. */
    public BooleanBinding rateNeeded() {
        return rateNeeded;
    }

    /** "1 USD =" */
    public StringBinding rateLabel() {
        return rateLabel;
    }

    /** The error text for one key of {@link #ERROR_KEYS}; empty when there's none. */
    public ReadOnlyStringProperty error(String key) {
        return errors.get(key);
    }

    public ReadOnlyBooleanProperty savingProperty() {
        return saving;
    }
}
