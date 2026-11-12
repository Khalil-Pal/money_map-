package com.jeib.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.db.Database;
import com.jeib.model.SupportedCurrency;
import com.jeib.repository.BudgetRepository;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.SettingsRepository;
import com.jeib.repository.TransactionRepository;
import com.jeib.service.BudgetService;
import com.jeib.service.CurrencyService;
import com.jeib.service.PreferencesService;
import com.jeib.service.ValidationException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsViewModelTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    private SettingsRepository settingsRepository;
    private CurrencyService currencies;
    private BudgetService budgets;

    @BeforeEach
    void setUp() throws SQLException {
        Database database = new Database(tempDir.resolve("test.db"));
        database.migrate();
        settingsRepository = new SettingsRepository(database);
        currencies = new CurrencyService(settingsRepository);
        currencies.setMainCurrency(SupportedCurrency.RUB);
        currencies.setRate(SupportedCurrency.USD, "92.5");
        budgets = new BudgetService(new BudgetRepository(database, CLOCK), new TransactionRepository(database, CLOCK),
                new CategoryRepository(database, CLOCK), currencies);
    }

    private SettingsViewModel loaded() throws SQLException {
        SettingsViewModel viewModel = new SettingsViewModel(currencies, budgets, () -> { }, message -> { });
        viewModel.apply(viewModel.loadData());
        return viewModel;
    }

    @Test
    void showsTheMainCurrencyAndTheOtherRates() throws SQLException {
        SettingsViewModel viewModel = loaded();

        assertEquals(SupportedCurrency.RUB, viewModel.mainCurrencyProperty().get());
        assertEquals(List.of(SupportedCurrency.USD, SupportedCurrency.ILS), viewModel.otherCurrencies());
        assertEquals("92.5", viewModel.rateText(SupportedCurrency.USD).get());
        assertEquals("", viewModel.rateText(SupportedCurrency.ILS).get()); // not set yet
    }

    @Test
    void changedRatesAreSavedAndABadOneDoesNotBlockTheOthers() throws SQLException {
        SettingsViewModel viewModel = loaded();

        Map<SupportedCurrency, String> problems = viewModel.saveRatesNow(Map.of(
                SupportedCurrency.USD, "95,25",
                SupportedCurrency.ILS, "abc"));

        assertEquals(List.of(SupportedCurrency.ILS), List.copyOf(problems.keySet()));
        assertEquals(new BigDecimal("95.25"), currencies.rates().rate(SupportedCurrency.USD).orElseThrow());
        assertTrue(currencies.rates().rate(SupportedCurrency.ILS).isEmpty());
    }

    @Test
    void emptyOrUnchangedRatesAreLeftAlone() throws SQLException {
        SettingsViewModel viewModel = loaded();

        assertEquals(Map.of(), viewModel.saveRatesNow(Map.of(SupportedCurrency.USD, "92.5", SupportedCurrency.ILS, " ")));
        assertTrue(currencies.rates().rate(SupportedCurrency.ILS).isEmpty());
    }

    @Test
    void pickingAnotherMainCurrencyWaitsForConfirmation() throws SQLException {
        SettingsViewModel viewModel = loaded();

        viewModel.requestMainCurrency(SupportedCurrency.USD);
        assertEquals(SupportedCurrency.USD, viewModel.pendingMainProperty().get());
        assertEquals(SupportedCurrency.RUB, currencies.mainCurrency().orElseThrow()); // nothing changed yet

        viewModel.cancelMainCurrency();
        assertNull(viewModel.pendingMainProperty().get());

        viewModel.requestMainCurrency(SupportedCurrency.RUB); // the current one: nothing to confirm
        assertNull(viewModel.pendingMainProperty().get());
    }

    @Test
    void switchingConvertsLimitsAndRates() throws SQLException {
        budgets.setLimit(null, "92500");
        SettingsViewModel viewModel = loaded();

        viewModel.switchNow(SupportedCurrency.USD);

        assertEquals(SupportedCurrency.USD, currencies.mainCurrency().orElseThrow());
        assertEquals(100_000, budgets.forMonth(YearMonth.of(2026, 11)).overall().limitMinor()); // $1,000.00
        viewModel.apply(viewModel.loadData());
        assertEquals(List.of(SupportedCurrency.ILS, SupportedCurrency.RUB), viewModel.otherCurrencies());
    }

    @Test
    void switchingToACurrencyWithoutARateIsRefused() throws SQLException {
        SettingsViewModel viewModel = loaded();

        assertThrows(ValidationException.class, () -> viewModel.switchNow(SupportedCurrency.ILS));
        assertEquals(SupportedCurrency.RUB, currencies.mainCurrency().orElseThrow());
    }

    @Test
    void theThemeIsRemembered() throws SQLException {
        PreferencesService preferences = new PreferencesService(settingsRepository);
        assertEquals(Optional.empty(), preferences.theme());

        preferences.setTheme("DARK");

        assertEquals(Optional.of("DARK"), preferences.theme());
    }
}
