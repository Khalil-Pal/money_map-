package com.jeib.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.db.Database;
import com.jeib.model.Category;
import com.jeib.model.Transaction;
import com.jeib.model.TransactionType;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.TransactionRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Category rules against a real, freshly migrated SQLite file (11 default categories). */
class CategoryServiceTest {

    // Default categories from V2. They use colors 0-9 and 13, so 10, 11 and 12 are free.
    private static final String FOOD = "a87a6066-c95c-48d4-b8c7-849a88bcab1f";
    private static final String RENT = "baa5872b-84d2-4d48-b6f0-a5bd9051e341";
    private static final String OTHER = "85e8c087-43da-4b4f-8800-5b7afb5a2592";
    private static final String SALARY = "ee0d97a3-17cf-4704-ad63-4fe0a9cd69bb";
    private static final String OTHER_INCOME = "d9417abf-d031-43ef-bf00-cf67abf03e24";

    private static final Clock CREATED = Clock.fixed(Instant.parse("2026-11-11T09:00:00Z"), ZoneOffset.UTC);
    private static final Clock LATER = Clock.fixed(Instant.parse("2026-11-11T18:30:00.250Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    private Database database;

    @BeforeEach
    void migrate() {
        database = new Database(tempDir.resolve("test.db"));
        database.migrate();
    }

    /** The service as it would run at a given time, so tests can tell "created" from "updated". */
    private CategoryService serviceAt(Clock clock) {
        return new CategoryService(new CategoryRepository(database, clock));
    }

    private CategoryService service() {
        return serviceAt(CREATED);
    }

    // ---- create ----------------------------------------------------------

    @Test
    void createTrimsTheNameAndTakesTheFirstFreeColor() throws SQLException {
        Category coffee = service().create("  Coffee   shops ", TransactionType.EXPENSE, null);

        assertEquals("Coffee shops", coffee.name());
        assertEquals(10, coffee.colorIndex());
        assertFalse(coffee.builtIn());
        assertTrue(service().list().contains(coffee));
    }

    @Test
    void colorsRepeatOnlyOnceAll14AreTaken() throws SQLException {
        CategoryService service = service();
        assertEquals(10, service.create("Coffee", TransactionType.EXPENSE, null).colorIndex());
        assertEquals(11, service.create("Gym", TransactionType.EXPENSE, null).colorIndex());
        assertEquals(12, service.create("Gifts", TransactionType.EXPENSE, null).colorIndex());

        // All 14 used once: start again from the lowest, least-used color.
        assertEquals(0, service.create("Books", TransactionType.EXPENSE, null).colorIndex());
        assertEquals(1, service.create("Phone", TransactionType.EXPENSE, null).colorIndex());
    }

    @Test
    void aPickedColorMustBeFreeWhileAnyColorIsFree() throws SQLException {
        CategoryService service = service();
        assertThrows(ValidationException.class, () -> service.create("Coffee", TransactionType.EXPENSE, 0)); // Food's
        assertEquals(12, service.create("Coffee", TransactionType.EXPENSE, 12).colorIndex());
        service.create("Gym", TransactionType.EXPENSE, 10);
        service.create("Gifts", TransactionType.EXPENSE, 11);

        // Nothing is free any more, so any color may be picked.
        assertEquals(0, service.create("Books", TransactionType.EXPENSE, 0).colorIndex());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 14})
    void aColorOutsideThePaletteIsRejected(int colorIndex) {
        assertThrows(ValidationException.class, () -> service().create("Coffee", TransactionType.EXPENSE, colorIndex));
    }

    @Test
    void namesAreUniquePerKindIgnoringCase() throws SQLException {
        ValidationException error = assertThrows(ValidationException.class,
                () -> service().create(" food ", TransactionType.EXPENSE, null));
        assertEquals("You already have an expense category called \"food\".", error.getMessage());

        // The same name is fine as an income category.
        service().create("Food", TransactionType.INCOME, null);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "12345678901234567890123456789012345678901"}) // the last is 41 characters
    void aNameMustNotBeBlankOrTooLong(String name) {
        assertThrows(ValidationException.class, () -> service().create(name, TransactionType.EXPENSE, null));
    }

    @Test
    void aNameOfExactly40CharactersIsAllowed() throws SQLException {
        String forty = "1234567890123456789012345678901234567890";
        assertEquals(forty, service().create(forty, TransactionType.EXPENSE, null).name());
    }

    // ---- update ----------------------------------------------------------

    @Test
    void updateRenamesRecolorsAndBumpsVersionForSync() throws SQLException {
        Category coffee = service().create("Coffee", TransactionType.EXPENSE, null);

        Category updated = serviceAt(LATER).update(coffee.id(), " Cafés ", 11);

        assertEquals("Cafés", updated.name());
        assertEquals(11, updated.colorIndex());
        assertEquals(updated, serviceAt(LATER).list().stream().filter(c -> c.id().equals(coffee.id())).findFirst().orElseThrow());
        assertEquals(List.of("2", "2026-11-11T09:00:00.000Z", "2026-11-11T18:30:00.250Z"),
                categoryRow(coffee.id(), "version", "created_at", "updated_at"));
    }

    @Test
    void aCategoryMayKeepItsOwnNameAndColor() throws SQLException {
        // Only the case changes, and color 0 is "taken" by Food itself.
        assertEquals("FOOD", service().update(FOOD, "FOOD", 0).name());
    }

    @Test
    void updateCannotTakeAnotherCategorysNameOrColor() throws SQLException {
        Category coffee = service().create("Coffee", TransactionType.EXPENSE, null);

        assertThrows(ValidationException.class, () -> service().update(coffee.id(), "rent", 10));
        assertThrows(ValidationException.class, () -> service().update(coffee.id(), "Coffee", 1)); // Rent's color
    }

    // ---- delete ----------------------------------------------------------

    @Test
    void deletingMovesLiveTransactionsToOther() throws SQLException {
        TransactionRepository transactions = new TransactionRepository(database, CREATED);
        Transaction lunch = expense(FOOD, 1_250);
        Transaction dinner = expense(FOOD, 3_400);
        Transaction alreadyDeleted = expense(FOOD, 900);
        Transaction rent = expense(RENT, 45_000);
        for (Transaction t : List.of(lunch, dinner, alreadyDeleted, rent)) {
            transactions.insert(t);
        }
        transactions.softDelete(alreadyDeleted.id());
        markAllSynced();

        List<String> moved = serviceAt(LATER).delete(FOOD);

        assertEquals(List.of(lunch.id(), dinner.id()).stream().sorted().toList(), moved.stream().sorted().toList());
        for (Transaction t : List.of(lunch, dinner)) {
            assertEquals(List.of(OTHER, "2", "2026-11-11T18:30:00.250Z", "PENDING"),
                    transactionRow(t.id(), "category_id", "version", "updated_at", "sync_state"));
        }
        assertEquals(List.of(FOOD, "SYNCED"), transactionRow(alreadyDeleted.id(), "category_id", "sync_state"));
        assertEquals(List.of(RENT, "1", "SYNCED"), transactionRow(rent.id(), "category_id", "version", "sync_state"));

        assertTrue(service().list().stream().noneMatch(c -> c.id().equals(FOOD)));
        assertEquals(List.of("1", "2", "2026-11-11T18:30:00.250Z"), categoryRow(FOOD, "deleted", "version", "updated_at"));
    }

    @Test
    void deletingAnIncomeCategoryMovesItsTransactionsToOtherIncome() throws SQLException {
        Transaction pay = Transaction.create(SALARY, TransactionType.INCOME, 280_000, "USD",
                LocalDate.parse("2026-11-01"), "November pay", CREATED);
        new TransactionRepository(database, CREATED).insert(pay);

        service().delete(SALARY);

        assertEquals(List.of(OTHER_INCOME), transactionRow(pay.id(), "category_id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {OTHER, OTHER_INCOME})
    void builtInsCannotBeDeleted(String builtInId) throws SQLException {
        assertThrows(ValidationException.class, () -> service().delete(builtInId));
        assertEquals(List.of("0"), categoryRow(builtInId, "deleted"));
    }

    @Test
    void theRepositoryAlsoRefusesToDeleteABuiltInAndRollsBack() throws SQLException {
        Transaction misc = expense(OTHER, 500);
        new TransactionRepository(database, CREATED).insert(misc);

        // Even if a caller skips the service, the moves are rolled back with the failed delete.
        CategoryRepository repository = new CategoryRepository(database, LATER);
        assertThrows(IllegalArgumentException.class, () -> repository.softDelete(OTHER, FOOD));
        assertEquals(List.of(OTHER, "1"), transactionRow(misc.id(), "category_id", "version"));
    }

    @Test
    void aDeletedCategorysNameAndColorCanBeReused() throws SQLException {
        service().delete(FOOD);

        Category food = service().create("Food", TransactionType.EXPENSE, null);

        assertEquals(0, food.colorIndex());
    }

    @Test
    void unknownOrDeletedCategoriesAreReported() throws SQLException {
        service().delete(FOOD);

        assertThrows(ValidationException.class, () -> service().delete(FOOD));
        assertThrows(ValidationException.class, () -> service().update(FOOD, "Food", 0));
        assertThrows(ValidationException.class, () -> service().delete("no-such-id"));
    }

    // ---- helpers ---------------------------------------------------------

    private static Transaction expense(String categoryId, long cents) {
        return Transaction.create(categoryId, TransactionType.EXPENSE, cents, "USD",
                LocalDate.parse("2026-11-05"), null, CREATED);
    }

    private void markAllSynced() throws SQLException {
        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE transactions SET sync_state = 'SYNCED'");
        }
    }

    private List<String> categoryRow(String id, String... columns) throws SQLException {
        return row("categories", id, columns);
    }

    private List<String> transactionRow(String id, String... columns) throws SQLException {
        return row("transactions", id, columns);
    }

    /** Raw column values as text, read straight from the table (bypassing the repositories). */
    private List<String> row(String table, String id, String... columns) throws SQLException {
        String sql = "SELECT " + String.join(", ", columns) + " FROM " + table + " WHERE id = ?";
        try (Connection connection = database.connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(), "no row " + id + " in " + table);
                String[] values = new String[columns.length];
                for (int i = 0; i < columns.length; i++) {
                    values[i] = rows.getString(i + 1);
                }
                return List.of(values);
            }
        }
    }
}
