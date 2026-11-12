package com.jeib.ui;

import com.jeib.model.ExchangeRates;
import com.jeib.model.SupportedCurrency;
import com.jeib.service.BudgetService;
import com.jeib.service.CurrencyService;
import com.jeib.service.ValidationException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;

/**
 * State for the Settings page: the main currency (switching converts limits and rates), the
 * exchange rates for the other two currencies, and where the data lives. The theme is the main
 * window's ({@link MainViewModel#setTheme}).
 */
public final class SettingsViewModel {

    private final CurrencyService currencies;
    private final BudgetService budgets;
    private final Runnable afterChange;
    private final Consumer<String> report;

    private final ObjectProperty<SupportedCurrency> mainCurrency = new SimpleObjectProperty<>();
    private final ObjectProperty<SupportedCurrency> pendingMain = new SimpleObjectProperty<>();
    private final Map<SupportedCurrency, StringProperty> rateTexts = new EnumMap<>(SupportedCurrency.class);
    private final Map<SupportedCurrency, StringProperty> rateErrors = new EnumMap<>(SupportedCurrency.class);
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private Map<SupportedCurrency, BigDecimal> savedRates = Map.of();

    /**
     * @param afterChange runs after a currency switch or rate change (every page shows money)
     * @param report      shows a short confirmation (a toast)
     */
    public SettingsViewModel(CurrencyService currencies, BudgetService budgets, Runnable afterChange,
                             Consumer<String> report) {
        this.currencies = currencies;
        this.budgets = budgets;
        this.afterChange = afterChange;
        this.report = report;
        for (SupportedCurrency currency : SupportedCurrency.values()) {
            StringProperty text = new SimpleStringProperty("");
            StringProperty rateError = new SimpleStringProperty("");
            text.addListener((obs, old, now) -> rateError.set(""));
            rateTexts.put(currency, text);
            rateErrors.put(currency, rateError);
        }
    }

    // ---- loading -----------------------------------------------------------

    ExchangeRates loadData() throws SQLException {
        return currencies.rates();
    }

    void apply(ExchangeRates rates) {
        mainCurrency.set(rates.main());
        pendingMain.set(null);
        savedRates = rates.rates();
        for (SupportedCurrency currency : SupportedCurrency.values()) {
            rateTexts.get(currency).set(text(savedRates.get(currency)));
            rateErrors.get(currency).set("");
        }
        error.set("");
    }

    public void load() {
        Task<ExchangeRates> task = new Task<>() {
            @Override
            protected ExchangeRates call() throws Exception {
                return loadData();
            }
        };
        task.setOnSucceeded(event -> apply(task.getValue()));
        task.setOnFailed(event -> error.set(task.getException().getMessage()));
        Thread.ofVirtual().name("jeib-settings").start(task);
    }

    private static String text(BigDecimal rate) {
        return rate == null ? "" : rate.stripTrailingZeros().toPlainString();
    }

    /** The currencies that need a rate: every supported one except the main currency. */
    public List<SupportedCurrency> otherCurrencies() {
        return List.of(SupportedCurrency.values()).stream().filter(c -> c != mainCurrency.get()).toList();
    }

    // ---- switching the main currency -------------------------------------------

    /** Picking another main currency first asks for confirmation (it converts limits). */
    public void requestMainCurrency(SupportedCurrency currency) {
        pendingMain.set(currency == mainCurrency.get() ? null : currency);
        error.set("");
    }

    public void cancelMainCurrency() {
        pendingMain.set(null);
    }

    /** Database work for {@link #confirmMainCurrency}; tests call it directly. */
    void switchNow(SupportedCurrency currency) throws SQLException {
        budgets.changeMainCurrency(currency);
    }

    public void confirmMainCurrency() {
        SupportedCurrency target = pendingMain.get();
        if (target == null) {
            return;
        }
        run(() -> {
            switchNow(target);
            return Map.of();
        }, "Your main currency is now " + target + ". Limits and rates were converted.");
    }

    // ---- exchange rates --------------------------------------------------------

    /**
     * Database work for {@link #saveRates}: saves each changed, non-empty rate. Returns the errors
     * by currency; empty means everything was saved. One bad rate doesn't stop the others.
     */
    Map<SupportedCurrency, String> saveRatesNow(Map<SupportedCurrency, String> texts) throws SQLException {
        Map<SupportedCurrency, String> problems = new LinkedHashMap<>();
        for (Map.Entry<SupportedCurrency, String> entry : texts.entrySet()) {
            String typed = entry.getValue().trim();
            if (typed.isEmpty() || typed.equals(text(savedRates.get(entry.getKey())))) {
                continue; // empty or unchanged: nothing to save
            }
            try {
                currencies.setRate(entry.getKey(), typed);
            } catch (ValidationException e) {
                problems.put(entry.getKey(), e.getMessage());
            }
        }
        return problems;
    }

    public void saveRates() {
        Map<SupportedCurrency, String> texts = new LinkedHashMap<>();
        otherCurrencies().forEach(c -> texts.put(c, rateTexts.get(c).get()));
        run(() -> saveRatesNow(texts), "Exchange rates saved.");
    }

    @FunctionalInterface
    private interface Work {
        Map<SupportedCurrency, String> run() throws SQLException;
    }

    private void run(Work work, String successMessage) {
        if (busy.get()) {
            return;
        }
        Task<Map<SupportedCurrency, String>> task = new Task<>() {
            @Override
            protected Map<SupportedCurrency, String> call() throws Exception {
                return work.run();
            }
        };
        task.setOnSucceeded(event -> {
            busy.set(false);
            Map<SupportedCurrency, String> problems = task.getValue();
            problems.forEach((currency, message) -> rateErrors.get(currency).set(message));
            if (problems.isEmpty()) {
                report.accept(successMessage);
            }
            afterChange.run();
            load();
        });
        task.setOnFailed(event -> {
            busy.set(false);
            Throwable e = task.getException();
            error.set(e instanceof ValidationException ? e.getMessage() : "Something went wrong: " + e.getMessage());
        });
        error.set("");
        busy.set(true);
        Thread.ofVirtual().name("jeib-settings-save").start(task);
    }

    // ---- for the controller --------------------------------------------------

    public ReadOnlyObjectProperty<SupportedCurrency> mainCurrencyProperty() {
        return mainCurrency;
    }

    /** The currency the user picked but hasn't confirmed yet, or null. */
    public ReadOnlyObjectProperty<SupportedCurrency> pendingMainProperty() {
        return pendingMain;
    }

    public StringProperty rateText(SupportedCurrency currency) {
        return rateTexts.get(currency);
    }

    public ReadOnlyStringProperty rateError(SupportedCurrency currency) {
        return rateErrors.get(currency);
    }

    public ReadOnlyStringProperty errorProperty() {
        return error;
    }

    public ReadOnlyBooleanProperty busyProperty() {
        return busy;
    }
}
