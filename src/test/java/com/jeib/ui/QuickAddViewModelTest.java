package com.jeib.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.db.Database;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.Transaction;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.SettingsRepository;
import com.jeib.repository.TransactionRepository;
import com.jeib.service.CategoryService;
import com.jeib.service.CurrencyService;
import com.jeib.service.TransactionCommands;
import com.jeib.service.TransactionService;
import com.jeib.service.UndoManager;
import com.jeib.service.ValidationException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The quick-add bar against a real database, without starting JavaFX. */
class QuickAddViewModelTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    private CurrencyService currencies;
    private TransactionService transactions;
    private TransactionCommands commands;
    private CategoryService categories;

    @BeforeEach
    void setUp() throws SQLException {
        Database database = new Database(tempDir.resolve("test.db"));
        database.migrate();
        currencies = new CurrencyService(new SettingsRepository(database));
        currencies.setMainCurrency(SupportedCurrency.RUB);
        CategoryRepository categoryRepository = new CategoryRepository(database, CLOCK);
        categories = new CategoryService(categoryRepository);
        transactions = new TransactionService(new TransactionRepository(database, CLOCK), categoryRepository,
                currencies, CLOCK);
        commands = new TransactionCommands(transactions, new UndoManager());
    }

    private QuickAddViewModel loaded() throws SQLException {
        QuickAddViewModel viewModel = new QuickAddViewModel(commands, categories, currencies, CLOCK, () -> { });
        viewModel.apply(viewModel.loadData());
        return viewModel;
    }

    private List<Transaction> november() throws SQLException {
        return transactions.listMonth(YearMonth.of(2026, 11));
    }

    @Test
    void thePreviewShowsWhatEnterWillSave() throws SQLException {
        QuickAddViewModel viewModel = loaded();

        viewModel.textProperty().set("250 food lunch");

        assertTrue(viewModel.previewProperty().get().startsWith("Expense · 250.00 ₽ · Food · “lunch”"));
        assertFalse(viewModel.previewIsErrorProperty().get());
    }

    @Test
    void thePreviewSaysWhenNoCategoryMatched() throws SQLException {
        QuickAddViewModel viewModel = loaded();

        viewModel.textProperty().set("+300 usd from home");

        assertTrue(viewModel.previewProperty().get()
                .startsWith("Income · +$300.00 · Other income (no category matched) · “from home”"));
    }

    @Test
    void badTextShowsAnErrorAndAnEmptyFieldShowsNothing() throws SQLException {
        QuickAddViewModel viewModel = loaded();

        viewModel.textProperty().set("lunch");
        assertTrue(viewModel.previewIsErrorProperty().get());

        viewModel.textProperty().set("");
        assertEquals("", viewModel.previewProperty().get());
        assertFalse(viewModel.previewIsErrorProperty().get());
    }

    @Test
    void addingSavesTheTransactionAndItCanBeUndone() throws SQLException {
        QuickAddViewModel viewModel = loaded();
        viewModel.textProperty().set("275 food lunch");

        viewModel.addNow(viewModel.parseNow());

        Transaction saved = november().getFirst();
        assertEquals(27_500, saved.amountMinor());
        assertEquals("lunch", saved.note());

        assertEquals(Optional.of("Add transaction"), commands.history().undo());
        assertTrue(november().isEmpty());
    }

    @Test
    void aCurrencyWithoutARateIsRefusedWhenSaving() throws SQLException {
        QuickAddViewModel viewModel = loaded();
        viewModel.textProperty().set("12 usd coffee");

        ValidationException error = assertThrows(ValidationException.class,
                () -> viewModel.addNow(viewModel.parseNow()));
        assertEquals("currency", error.field());
        assertTrue(november().isEmpty());
    }
}
