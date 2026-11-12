package com.jeib.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.db.Database;
import com.jeib.model.Transaction;
import com.jeib.model.TransactionType;
import com.jeib.model.SupportedCurrency;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.SettingsRepository;
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
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Transaction rules against a real, freshly migrated SQLite file. */
class TransactionServiceTest {

    private static final String FOOD = "a87a6066-c95c-48d4-b8c7-849a88bcab1f";
    private static final String RENT = "baa5872b-84d2-4d48-b6f0-a5bd9051e341";
    private static final String SALARY = "ee0d97a3-17cf-4704-ad63-4fe0a9cd69bb";

    private static final Clock CREATED = Clock.fixed(Instant.parse("2026-11-11T09:00:00Z"), ZoneOffset.UTC);
    private static final Clock LATER = Clock.fixed(Instant.parse("2026-11-12T20:15:30.500Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.parse("2026-11-11");

    @TempDir
    Path tempDir;

    private Database database;

    @BeforeEach
    void migrate() throws SQLException {
        database = new Database(tempDir.resolve("test.db"));
        database.migrate();
        currencies().setMainCurrency(SupportedCurrency.USD);
    }

    private CurrencyService currencies() {
        return new CurrencyService(new SettingsRepository(database));
    }

    private TransactionService serviceAt(Clock clock) {
        return new TransactionService(new TransactionRepository(database, clock),
                new CategoryRepository(database, clock), currencies(), clock);
    }

    private TransactionService service() {
        return serviceAt(CREATED);
    }

    private static TransactionInput lunch() {
        return new TransactionInput(TransactionType.EXPENSE, "12.50", null, TODAY, FOOD, "  Lunch with Sam ");
    }

    // ---- add -------------------------------------------------------------

    @Test
    void addSavesACleanedUpTransaction() throws SQLException {
        Transaction saved = service().add(lunch());

        assertEquals(1_250, saved.amountMinor());
        assertEquals("Lunch with Sam", saved.note());
        assertEquals("USD", saved.currency());
        assertEquals(1, saved.version());
        assertEquals(List.of(saved), service().listMonth(YearMonth.of(2026, 11)));
    }

    @Test
    void aBlankNoteIsStoredAsNoNote() throws SQLException {
        Transaction saved = service().add(new TransactionInput(TransactionType.EXPENSE, "3", null, TODAY, FOOD, "   "));
        assertNull(saved.note());
    }

    @Test
    void incomeGoesIntoAnIncomeCategory() throws SQLException {
        Transaction pay = service().add(new TransactionInput(TransactionType.INCOME, "2800", null, TODAY, SALARY, null));
        assertEquals(280_000, pay.amountMinor());
    }

    @ParameterizedTest(name = "\"{0}\" is rejected")
    @NullSource
    @ValueSource(strings = {"", "  ", "abc", "-5", "0", "0.00", "1.234", "12.", "1000000000.00", "99999999999999999999"})
    void badAmountsAreRejected(String amount) {
        ValidationException error = assertThrows(ValidationException.class,
                () -> service().add(new TransactionInput(TransactionType.EXPENSE, amount, null, TODAY, FOOD, null)));
        assertEquals("amount", error.field());
    }

    @Test
    void theLargestAllowedAmountIsAccepted() throws SQLException {
        Transaction big = service().add(new TransactionInput(TransactionType.EXPENSE, "999999999.99", null, TODAY, RENT, null));
        assertEquals(TransactionService.MAX_AMOUNT_MINOR, big.amountMinor());
    }

    @Test
    void theDateIsRequiredAndAtMostAYearAhead() throws SQLException {
        assertEquals("date", assertThrows(ValidationException.class, () -> service().add(
                new TransactionInput(TransactionType.EXPENSE, "5", null, null, FOOD, null))).field());
        assertEquals("date", assertThrows(ValidationException.class, () -> service().add(
                new TransactionInput(TransactionType.EXPENSE, "5", null, TODAY.plusYears(1).plusDays(1), FOOD, null))).field());

        // Exactly a year ahead (a planned bill) and long ago are both fine.
        service().add(new TransactionInput(TransactionType.EXPENSE, "5", null, TODAY.plusYears(1), FOOD, null));
        service().add(new TransactionInput(TransactionType.EXPENSE, "5", null, LocalDate.parse("2019-02-28"), FOOD, null));
    }

    @Test
    void theTypeIsRequired() {
        ValidationException error = assertThrows(ValidationException.class,
                () -> service().add(new TransactionInput(null, "5", null, TODAY, FOOD, null)));
        assertEquals("type", error.field());
    }

    @ParameterizedTest(name = "{0} with category {1}")
    @CsvSource(nullValues = "NULL", value = {
            "EXPENSE, NULL",
            "EXPENSE, no-such-category",
            "EXPENSE, ee0d97a3-17cf-4704-ad63-4fe0a9cd69bb", // Salary is an income category
            "INCOME,  a87a6066-c95c-48d4-b8c7-849a88bcab1f", // Food is an expense category
    })
    void theCategoryMustExistAndMatchTheType(TransactionType type, String categoryId) {
        ValidationException error = assertThrows(ValidationException.class,
                () -> service().add(new TransactionInput(type, "5", null, TODAY, categoryId, null)));
        assertEquals("category", error.field());
    }

    @Test
    void aDeletedCategoryCannotBeUsed() throws SQLException {
        new CategoryService(new CategoryRepository(database, CREATED)).delete(FOOD);

        assertThrows(ValidationException.class, () -> service().add(lunch()));
    }

    @Test
    void theMismatchMessageExplainsWhatToDo() {
        ValidationException error = assertThrows(ValidationException.class,
                () -> service().add(new TransactionInput(TransactionType.EXPENSE, "5", null, TODAY, SALARY, null)));
        assertEquals("\"Salary\" is an income category. Pick an expense category.", error.getMessage());
    }

    @Test
    void notesHaveALengthLimit() throws SQLException {
        String max = "n".repeat(TransactionService.MAX_NOTE_LENGTH);
        assertEquals(max, service().add(new TransactionInput(TransactionType.EXPENSE, "5", null, TODAY, FOOD, max)).note());

        ValidationException error = assertThrows(ValidationException.class,
                () -> service().add(new TransactionInput(TransactionType.EXPENSE, "5", null, TODAY, FOOD, max + "n")));
        assertEquals("note", error.field());
    }

    // ---- edit ------------------------------------------------------------

    @Test
    void editChangesTheFieldsAndBumpsVersionForSync() throws SQLException {
        Transaction original = service().add(lunch());
        markAllSynced();

        Transaction edited = serviceAt(LATER).edit(original.id(),
                new TransactionInput(TransactionType.EXPENSE, "40", null, TODAY.minusDays(1), RENT, "Fixed"));

        assertEquals(edited, new TransactionRepository(database, LATER).findById(original.id()).orElseThrow());
        assertEquals(4_000, edited.amountMinor());
        assertEquals(RENT, edited.categoryId());
        assertEquals(2, edited.version());
        assertEquals(original.createdAt(), edited.createdAt());
        assertEquals(Instant.parse("2026-11-12T20:15:30.500Z"), edited.updatedAt());
        assertEquals("PENDING", syncState(original.id()));
    }

    @Test
    void editValidatesLikeAdd() throws SQLException {
        Transaction original = service().add(lunch());

        assertThrows(ValidationException.class, () -> service().edit(original.id(),
                new TransactionInput(TransactionType.EXPENSE, "0", null, TODAY, FOOD, null)));
        assertEquals(1, new TransactionRepository(database, CREATED).findById(original.id()).orElseThrow().version());
    }

    @Test
    void aDeletedOrUnknownTransactionCannotBeEdited() throws SQLException {
        Transaction original = service().add(lunch());
        service().delete(original.id());

        assertThrows(ValidationException.class, () -> service().edit(original.id(), lunch()));
        assertThrows(ValidationException.class, () -> service().edit("no-such-id", lunch()));
    }

    @Test
    void anEditNeverOverwritesAChangeMadeInBetween() throws SQLException {
        Transaction original = service().add(lunch());
        TransactionRepository repository = new TransactionRepository(database, LATER);
        Transaction stale = new Transaction(original.id(), null, FOOD, TransactionType.EXPENSE, 9_900, "USD",
                TODAY, "stale", original.createdAt(), LATER.instant(), 2);

        // Someone else saves version 2 first (later on: a sync pull).
        assertTrue(repository.update(new Transaction(original.id(), null, RENT, TransactionType.EXPENSE, 7_000, "USD",
                TODAY, "first", original.createdAt(), LATER.instant(), 2)));

        // Our edit was also based on version 1, so it must not win silently.
        assertEquals(false, repository.update(stale));
        assertEquals("first", repository.findById(original.id()).orElseThrow().note());
    }

    // ---- delete ----------------------------------------------------------

    @Test
    void deleteHidesTheTransactionAndCanOnlyHappenOnce() throws SQLException {
        Transaction original = service().add(lunch());

        service().delete(original.id());

        assertTrue(service().listMonth(YearMonth.of(2026, 11)).isEmpty());
        assertThrows(ValidationException.class, () -> service().delete(original.id()));
    }

    // ---- currencies --------------------------------------------------------

    @Test
    void noCurrencyMeansTheMainCurrency() throws SQLException {
        currencies().setRate(SupportedCurrency.RUB, "0.0108"); // a switch needs a rate for the new main
        currencies().setMainCurrency(SupportedCurrency.RUB);
        assertEquals("RUB", service().add(lunch()).currency());
    }

    @Test
    void anotherCurrencyNeedsItsRateFirst() throws SQLException {
        TransactionInput shekels = new TransactionInput(TransactionType.EXPENSE, "45", SupportedCurrency.ILS, TODAY, FOOD, null);
        ValidationException error = assertThrows(ValidationException.class, () -> service().add(shekels));
        assertEquals("currency", error.field());

        currencies().setRate(SupportedCurrency.ILS, "0.27");
        assertEquals("ILS", service().add(shekels).currency());
    }

    @Test
    void nothingCanBeAddedBeforeAMainCurrencyIsChosen() throws SQLException {
        new SettingsRepository(database).save(java.util.Collections.singletonMap("main_currency", null));

        ValidationException error = assertThrows(ValidationException.class, () -> service().add(lunch()));
        assertEquals("currency", error.field());
    }

    @Test
    void editCanChangeTheCurrency() throws SQLException {
        currencies().setRate(SupportedCurrency.RUB, "0.0108");
        Transaction original = service().add(lunch());

        Transaction edited = serviceAt(LATER).edit(original.id(),
                new TransactionInput(TransactionType.EXPENSE, "950", SupportedCurrency.RUB, TODAY, FOOD, null));

        assertEquals("RUB", edited.currency());
        assertEquals(95_000, edited.amountMinor());
    }

    // ---- helpers ---------------------------------------------------------

    private void markAllSynced() throws SQLException {
        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE transactions SET sync_state = 'SYNCED'");
        }
    }

    private String syncState(String id) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement("SELECT sync_state FROM transactions WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet row = statement.executeQuery()) {
                assertTrue(row.next());
                return row.getString(1);
            }
        }
    }
}
