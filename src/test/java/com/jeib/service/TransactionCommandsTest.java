package com.jeib.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.db.Database;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.Transaction;
import com.jeib.model.TransactionType;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.SettingsRepository;
import com.jeib.repository.TransactionRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Undo and redo of real changes, checked in the database (deleted flag, version, sync state). */
class TransactionCommandsTest {

    private static final String FOOD = "a87a6066-c95c-48d4-b8c7-849a88bcab1f";
    private static final String RENT = "baa5872b-84d2-4d48-b6f0-a5bd9051e341";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.parse("2026-11-12");

    @TempDir
    Path tempDir;

    private Database database;
    private TransactionService service;
    private UndoManager history;
    private TransactionCommands commands;

    @BeforeEach
    void setUp() throws SQLException {
        database = new Database(tempDir.resolve("test.db"));
        database.migrate();
        CurrencyService currencies = new CurrencyService(new SettingsRepository(database));
        currencies.setMainCurrency(SupportedCurrency.RUB);
        currencies.setRate(SupportedCurrency.USD, "92.5");
        service =new TransactionService(new TransactionRepository(database, CLOCK),
                new CategoryRepository(database, CLOCK), currencies, CLOCK);
        history = new UndoManager();
        commands = new TransactionCommands(service, history);
    }

    private static TransactionInput lunch(String amount) {
        return new TransactionInput(TransactionType.EXPENSE, amount, null, TODAY, FOOD, "Lunch");
    }

    private List<Transaction> visible() throws SQLException {
        return service.listMonth(YearMonth.of(2026, 11));
    }

    private int version(String id) throws SQLException {
        return service.find(id).orElseThrow().version();
    }

    @Test
    void undoingAnAddHidesItAndRedoBringsBackTheSameRow() throws SQLException {
        Transaction added = commands.add(lunch("275"));

        assertEquals(Optional.of("Add transaction"), history.undo());
        assertTrue(visible().isEmpty());

        assertEquals(Optional.of("Add transaction"), history.redo());
        assertEquals(List.of(added.id()), visible().stream().map(Transaction::id).toList());
        assertEquals(3, version(added.id())); // added, deleted, restored: every step is a synced change
    }

    @Test
    void undoingADeleteRestoresTheRowForSync() throws SQLException {
        Transaction added = commands.add(lunch("275"));
        markSynced(added.id());

        commands.delete(added.id());
        assertTrue(visible().isEmpty());

        history.undo();

        assertEquals(1, visible().size());
        assertEquals(3, version(added.id()));
        assertEquals("PENDING", syncState(added.id()));
    }

    @Test
    void undoingAnEditPutsTheOldValuesBackAndRedoReappliesTheNewOnes() throws SQLException {
        Transaction added = commands.add(lunch("275"));
        commands.edit(added.id(), new TransactionInput(TransactionType.EXPENSE, "40", SupportedCurrency.USD,
                TODAY.minusDays(1), RENT, "Changed"));
        assertEquals(4_000, visible().getFirst().amountMinor());

        history.undo();
        Transaction undone = visible().getFirst();
        assertEquals(27_500, undone.amountMinor());
        assertEquals("RUB", undone.currency());
        assertEquals(FOOD, undone.categoryId());
        assertEquals("Lunch", undone.note());
        assertEquals(TODAY, undone.occurredOn());

        history.redo();
        Transaction redone = visible().getFirst();
        assertEquals(4_000, redone.amountMinor());
        assertEquals("USD", redone.currency());
        assertEquals(RENT, redone.categoryId());
        assertEquals(4, redone.version());
    }

    @Test
    void anInvalidChangeIsNotOnTheUndoStack() throws SQLException {
        Transaction added = commands.add(lunch("275"));

        assertThrows(ValidationException.class, () -> commands.edit(added.id(), lunch("0")));

        // The only thing to undo is the add.
        assertEquals(Optional.of("Add transaction"), history.undo());
        assertEquals(Optional.empty(), history.undo());
    }

    @Test
    void severalStepsUndoInReverseOrder() throws SQLException {
        Transaction a = commands.add(lunch("1"));
        Transaction b = commands.add(lunch("2"));
        commands.delete(a.id());

        history.undo(); // a comes back
        history.undo(); // b goes away

        assertEquals(List.of(a.id()), visible().stream().map(Transaction::id).toList());
        assertFalse(visible().stream().anyMatch(t -> t.id().equals(b.id())));
    }

    @Test
    void restoreOnlyWorksOnADeletedRow() throws SQLException {
        Transaction added = commands.add(lunch("275"));
        assertThrows(ValidationException.class, () -> service.restore(added.id()));
        assertThrows(ValidationException.class, () -> service.restore("no-such-id"));
    }

    // ---- helpers ---------------------------------------------------------

    private void markSynced(String id) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE transactions SET sync_state = 'SYNCED' WHERE id = ?")) {
            statement.setString(1, id);
            statement.executeUpdate();
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
