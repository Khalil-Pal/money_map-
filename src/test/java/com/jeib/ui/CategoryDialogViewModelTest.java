package com.jeib.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.db.Database;
import com.jeib.model.Category;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.TransactionType;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.SettingsRepository;
import com.jeib.repository.TransactionRepository;
import com.jeib.service.CategoryService;
import com.jeib.service.CurrencyService;
import com.jeib.service.TransactionInput;
import com.jeib.service.TransactionService;
import com.jeib.service.ValidationException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The category dialog's logic against a real database; defaults use colors 0-9 and 13. */
class CategoryDialogViewModelTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    private CategoryService categories;
    private TransactionService transactions;

    @BeforeEach
    void setUp() throws SQLException {
        Database database = new Database(tempDir.resolve("test.db"));
        database.migrate();
        CurrencyService currencies = new CurrencyService(new SettingsRepository(database));
        currencies.setMainCurrency(SupportedCurrency.RUB);
        CategoryRepository repository = new CategoryRepository(database, CLOCK);
        categories = new CategoryService(repository);
        transactions = new TransactionService(new TransactionRepository(database, CLOCK), repository, currencies, CLOCK);
    }

    private CategoryDialogViewModel opened(Category editing, TransactionType kind) throws SQLException {
        CategoryDialogViewModel viewModel = new CategoryDialogViewModel(categories, editing, kind);
        viewModel.apply(viewModel.loadData());
        return viewModel;
    }

    private Category named(String name) throws SQLException {
        return categories.list().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void aNewCategoryStartsOnTheFirstFreeColorAndGreysOutUsedOnes() throws SQLException {
        CategoryDialogViewModel viewModel = opened(null, TransactionType.EXPENSE);

        assertEquals("New expense category", viewModel.title());
        assertEquals(10, viewModel.colorProperty().get());
        assertTrue(viewModel.isTaken(0));   // Food
        assertFalse(viewModel.isTaken(10));
        assertFalse(viewModel.allColorsUsed());
        assertFalse(viewModel.canDelete());
    }

    @Test
    void savingANewCategoryCreatesIt() throws SQLException {
        CategoryDialogViewModel viewModel = opened(null, TransactionType.INCOME);

        assertEquals("Category “Scholarship” added.", viewModel.saveNow(" Scholarship ", TransactionType.INCOME, 11));

        Category created = named("Scholarship");
        assertEquals(TransactionType.INCOME, created.kind());
        assertEquals(11, created.colorIndex());
    }

    @Test
    void editingKeepsItsOwnColorAvailableAndSavesChanges() throws SQLException {
        CategoryDialogViewModel viewModel = opened(named("Food"), TransactionType.EXPENSE);

        assertEquals("Edit category", viewModel.title());
        assertEquals("Food", viewModel.nameProperty().get());
        assertFalse(viewModel.isTaken(0)); // its own color

        viewModel.saveNow("Groceries", TransactionType.EXPENSE, 12);

        assertEquals(12, named("Groceries").colorIndex());
    }

    @Test
    void onceAllColorsAreUsedTheyCanRepeat() throws SQLException {
        for (String name : new String[] {"A", "B", "C"}) {
            categories.create(name, TransactionType.EXPENSE, null);
        }
        CategoryDialogViewModel viewModel = opened(null, TransactionType.EXPENSE);

        assertTrue(viewModel.allColorsUsed());
        assertFalse(viewModel.isTaken(0));
    }

    @Test
    void deletingAsksFirstThenMovesTheTransactionsToOther() throws SQLException {
        Category food = named("Food");
        transactions.add(new TransactionInput(TransactionType.EXPENSE, "250", null, LocalDate.parse("2026-11-12"),
                food.id(), "lunch"));
        CategoryDialogViewModel viewModel = opened(food, TransactionType.EXPENSE);

        assertTrue(viewModel.canDelete());
        viewModel.delete(message -> { });
        assertTrue(viewModel.confirmingDeleteProperty().get()); // first click only asks
        assertTrue(viewModel.deleteQuestion().contains("move to Other"));
        viewModel.cancelDelete();
        assertFalse(viewModel.confirmingDeleteProperty().get());

        assertEquals("Category “Food” deleted. 1 transaction moved to Other.", viewModel.deleteNow());
        assertEquals(named("Other").id(), transactions.listMonth(java.time.YearMonth.of(2026, 11)).getFirst().categoryId());
    }

    @Test
    void builtInsCanBeEditedButNotDeleted() throws SQLException {
        CategoryDialogViewModel viewModel = opened(named("Other"), TransactionType.EXPENSE);

        assertFalse(viewModel.canDelete());
        assertThrows(ValidationException.class, viewModel::deleteNow);
    }
}
