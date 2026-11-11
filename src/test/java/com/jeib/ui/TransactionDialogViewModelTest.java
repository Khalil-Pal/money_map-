package com.jeib.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.db.Database;
import com.jeib.model.Category;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.Transaction;
import com.jeib.model.TransactionType;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.SettingsRepository;
import com.jeib.repository.TransactionRepository;
import com.jeib.service.CategoryService;
import com.jeib.service.CurrencyService;
import com.jeib.service.TransactionCommands;
import com.jeib.service.TransactionService;
import com.jeib.service.UndoManager;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The dialog's logic against a real database, without starting JavaFX: the tests call the same
 * steps the background tasks run (loadData → apply, request → perform).
 */
class TransactionDialogViewModelTest {

    private static final String FOOD = "a87a6066-c95c-48d4-b8c7-849a88bcab1f";
    private static final String SALARY = "ee0d97a3-17cf-4704-ad63-4fe0a9cd69bb";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    private Database database;
    private CurrencyService currencies;
    private TransactionService transactions;

    @BeforeEach
    void setUp() throws SQLException {
        database = new Database(tempDir.resolve("test.db"));
        database.migrate();
        currencies = new CurrencyService(new SettingsRepository(database));
        currencies.setMainCurrency(SupportedCurrency.RUB);
        CategoryRepository categoryRepository = new CategoryRepository(database, CLOCK);
        transactions = new TransactionService(new TransactionRepository(database, CLOCK), categoryRepository,
                currencies, CLOCK);
    }

    /** A dialog as Dialogs opens it: prepared, then loaded. */
    private TransactionDialogViewModel opened(Transaction editing) throws SQLException {
        TransactionDialogViewModel viewModel = new TransactionDialogViewModel(new TransactionCommands(transactions, new UndoManager()),
                new CategoryService(new CategoryRepository(database, CLOCK)), currencies, CLOCK);
        if (editing == null) {
            viewModel.startNew();
        } else {
            viewModel.startEdit(editing);
        }
        viewModel.apply(viewModel.loadData());
        return viewModel;
    }

    private List<Transaction> november() throws SQLException {
        return transactions.listMonth(YearMonth.of(2026, 11));
    }

    @Test
    void aNewTransactionStartsWithSensibleDefaults() throws SQLException {
        TransactionDialogViewModel viewModel = opened(null);

        assertEquals("New transaction", viewModel.titleProperty().get());
        assertEquals(TransactionType.EXPENSE, viewModel.typeProperty().get());
        assertEquals(SupportedCurrency.RUB, viewModel.currencyProperty().get());
        assertEquals(LocalDate.parse("2026-11-12"), viewModel.dateProperty().get());
        assertNull(viewModel.categoryIdProperty().get());
        assertFalse(viewModel.rateNeeded().get());
        assertTrue(viewModel.categoriesForType().stream().allMatch(c -> c.kind() == TransactionType.EXPENSE));
        assertEquals(8, viewModel.categoriesForType().size());
    }

    @Test
    void savingAValidFormAddsTheTransaction() throws SQLException {
        TransactionDialogViewModel viewModel = opened(null);
        viewModel.amountProperty().set("275");
        viewModel.categoryIdProperty().set(FOOD);
        viewModel.noteProperty().set("Lunch");

        assertEquals(Map.of(), viewModel.perform(viewModel.request()));

        Transaction saved = november().getFirst();
        assertEquals(27_500, saved.amountMinor());
        assertEquals("RUB", saved.currency());
        assertEquals("Lunch", saved.note());
    }

    @Test
    void errorsComeBackForTheirFieldAndClearWhenTheFieldChanges() throws SQLException {
        TransactionDialogViewModel viewModel = opened(null);
        viewModel.categoryIdProperty().set(FOOD);

        Map<String, String> errors = viewModel.perform(viewModel.request()); // no amount
        viewModel.showErrors(errors);

        assertEquals(List.of("amount"), List.copyOf(errors.keySet()));
        assertEquals("Enter an amount.", viewModel.error("amount").get());
        assertTrue(november().isEmpty());

        viewModel.amountProperty().set("5");
        assertEquals("", viewModel.error("amount").get());
    }

    @Test
    void switchingToIncomeShowsIncomeCategoriesAndDropsAnExpenseCategory() throws SQLException {
        TransactionDialogViewModel viewModel = opened(null);
        viewModel.categoryIdProperty().set(FOOD);

        viewModel.typeProperty().set(TransactionType.INCOME);

        assertNull(viewModel.categoryIdProperty().get());
        assertEquals(List.of("Salary", "Freelance", "Other income"),
                viewModel.categoriesForType().stream().map(Category::name).toList());
    }

    @Test
    void anotherCurrencyAsksForItsRateAndSavesItFirst() throws SQLException {
        TransactionDialogViewModel viewModel = opened(null);
        viewModel.amountProperty().set("300");
        viewModel.typeProperty().set(TransactionType.INCOME);
        viewModel.categoryIdProperty().set(SALARY);
        viewModel.currencyProperty().set(SupportedCurrency.USD);

        assertTrue(viewModel.rateNeeded().get());
        assertEquals("1 USD =", viewModel.rateLabel().get());
        assertEquals("", viewModel.rateProperty().get());
        assertEquals(Map.of("rate", "Enter a rate like 92.50 (at most 6 decimals)."),
                viewModel.perform(viewModel.request()));

        viewModel.rateProperty().set("92,5");
        assertEquals(Map.of(), viewModel.perform(viewModel.request()));

        assertEquals(new BigDecimal("92.5"), currencies.rates().rate(SupportedCurrency.USD).orElseThrow());
        assertEquals("USD", november().getFirst().currency());
    }

    @Test
    void aSavedRateIsPrefilledAndNotRewrittenIfUnchanged() throws SQLException {
        currencies.setRate(SupportedCurrency.ILS, "25");
        TransactionDialogViewModel viewModel = opened(null);

        viewModel.currencyProperty().set(SupportedCurrency.ILS);

        assertEquals("25", viewModel.rateProperty().get());
        assertFalse(viewModel.request().saveRate());

        viewModel.rateProperty().set("25.40");
        assertTrue(viewModel.request().saveRate());
    }

    @Test
    void onlyAnEditedTransactionCanBeDeletedAndTheDeleteCanBeUndone() throws SQLException {
        assertFalse(opened(null).editingProperty().get());

        TransactionDialogViewModel adding = opened(null);
        adding.amountProperty().set("12.5");
        adding.categoryIdProperty().set(FOOD);
        adding.perform(adding.request());
        Transaction original = november().getFirst();

        TransactionDialogViewModel editing = opened(original);
        assertTrue(editing.editingProperty().get());
        editing.deleteNow(original.id());
        assertTrue(november().isEmpty());
    }

    @Test
    void editingFillsTheFormAndSavesTheChange() throws SQLException {
        TransactionDialogViewModel adding = opened(null);
        adding.amountProperty().set("12.5");
        adding.categoryIdProperty().set(FOOD);
        adding.noteProperty().set("Coffee");
        adding.perform(adding.request());
        Transaction original = november().getFirst();

        TransactionDialogViewModel editing = opened(original);
        assertEquals("Edit transaction", editing.titleProperty().get());
        assertEquals("12.50", editing.amountProperty().get());
        assertEquals(FOOD, editing.categoryIdProperty().get());
        assertEquals("Coffee", editing.noteProperty().get());

        editing.amountProperty().set("14");
        assertEquals(Map.of(), editing.perform(editing.request()));

        Transaction edited = november().getFirst();
        assertEquals(original.id(), edited.id());
        assertEquals(1_400, edited.amountMinor());
        assertEquals(2, edited.version());
    }
}
