package com.jeib.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.db.Database;
import com.jeib.model.SupportedCurrency;
import com.jeib.repository.BudgetRepository;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.SettingsRepository;
import com.jeib.repository.TransactionRepository;
import com.jeib.service.BudgetService;
import com.jeib.service.BudgetService.LimitLine;
import com.jeib.service.CurrencyService;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LimitDialogViewModelTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    @TempDir
    Path tempDir;

    private BudgetService budgets;

    @BeforeEach
    void setUp() throws SQLException {
        Database database = new Database(tempDir.resolve("test.db"));
        database.migrate();
        CurrencyService currencies = new CurrencyService(new SettingsRepository(database));
        currencies.setMainCurrency(SupportedCurrency.RUB);
        CategoryRepository categories = new CategoryRepository(database, CLOCK);
        budgets = new BudgetService(new BudgetRepository(database, CLOCK), new TransactionRepository(database, CLOCK),
                categories, currencies);
    }

    private LimitLine food() throws SQLException {
        return budgets.forMonth(NOVEMBER).categories().getFirst(); // Food is palette color 0
    }

    @Test
    void anEmptyCategoryLimitCanBeSet() throws SQLException {
        LimitDialogViewModel viewModel = new LimitDialogViewModel(budgets, food(), SupportedCurrency.RUB);

        assertEquals("Food limit", viewModel.title());
        assertTrue(viewModel.hint().contains("In RUB"));
        assertFalse(viewModel.hasLimit());
        assertEquals("", viewModel.amountProperty().get());

        viewModel.saveNow("10000");
        assertEquals(1_000_000, food().limitMinor());
    }

    @Test
    void anExistingLimitIsPrefilledAndCanBeRemoved() throws SQLException {
        budgets.setLimit(food().category().id(), "2500.50");
        LimitDialogViewModel viewModel = new LimitDialogViewModel(budgets, food(), SupportedCurrency.RUB);

        assertTrue(viewModel.hasLimit());
        assertEquals("2500.5", viewModel.amountProperty().get());

        viewModel.removeNow();
        assertNull(food().limitMinor());
    }

    @Test
    void theOverallLimitHasItsOwnTitle() throws SQLException {
        LimitDialogViewModel viewModel = new LimitDialogViewModel(budgets, budgets.forMonth(NOVEMBER).overall(),
                SupportedCurrency.RUB);

        assertEquals("Monthly limit", viewModel.title());
        viewModel.saveNow("15000");
        assertEquals(1_500_000, budgets.forMonth(NOVEMBER).overall().limitMinor());
    }
}
