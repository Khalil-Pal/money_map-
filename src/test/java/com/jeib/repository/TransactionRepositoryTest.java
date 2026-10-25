package com.jeib.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.db.Database;
import com.jeib.model.Transaction;
import com.jeib.model.TransactionType;
import java.nio.file.Path;
import java.sql.Connection;
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

class TransactionRepositoryTest {

    private static final String FOOD = "a87a6066-c95c-48d4-b8c7-849a88bcab1f";
    private static final String SALARY = "ee0d97a3-17cf-4704-ad63-4fe0a9cd69bb";
    private static final Clock CREATED = Clock.fixed(Instant.parse("2026-10-25T09:30:00.123Z"), ZoneOffset.UTC);
    private static final Clock LATER = Clock.fixed(Instant.parse("2026-10-25T18:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    private Database database;
    private TransactionRepository repository;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("test.db"));
        database.migrate();
        repository = new TransactionRepository(database, LATER);
    }

    private static Transaction expense(long cents, String date, String note) {
        return Transaction.create(FOOD, TransactionType.EXPENSE, cents, "USD", LocalDate.parse(date), note, CREATED);
    }

    @Test
    void insertedTransactionReadsBackEqual() throws SQLException {
        Transaction groceries = expense(8_640, "2026-10-24", "Groceries");
        repository.insert(groceries);

        assertEquals(groceries, repository.findById(groceries.id()).orElseThrow());
    }

    @Test
    void findByMonthReturnsOnlyThatMonthNewestFirst() throws SQLException {
        Transaction lastDayOfSeptember = expense(1_000, "2026-09-30", "Sep 30");
        Transaction firstOfOctober = expense(2_000, "2026-10-01", "Oct 1");
        Transaction lastOfOctober = expense(3_000, "2026-10-31", "Oct 31");
        Transaction firstOfNovember = expense(4_000, "2026-11-01", "Nov 1");
        Transaction salary = Transaction.create(SALARY, TransactionType.INCOME, 280_000, "USD",
                LocalDate.parse("2026-10-15"), "Salary", CREATED);
        for (Transaction t : List.of(lastDayOfSeptember, firstOfOctober, lastOfOctober, firstOfNovember, salary)) {
            repository.insert(t);
        }

        List<String> notes = repository.findByMonth(YearMonth.of(2026, 10)).stream().map(Transaction::note).toList();

        assertEquals(List.of("Oct 31", "Salary", "Oct 1"), notes);
    }

    @Test
    void softDeleteHidesTheRowButKeepsItForUndoAndSync() throws SQLException {
        Transaction cinema = expense(2_800, "2026-10-12", "Cinema");
        repository.insert(cinema);

        assertTrue(repository.softDelete(cinema.id()));

        assertTrue(repository.findByMonth(YearMonth.of(2026, 10)).isEmpty());
        Transaction deleted = repository.findById(cinema.id()).orElseThrow();
        assertEquals(2, deleted.version());
        assertEquals(Instant.parse("2026-10-25T18:00:00Z"), deleted.updatedAt());
        assertEquals(cinema.createdAt(), deleted.createdAt());
        try (Connection connection = database.connect(); Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(
                     "SELECT deleted, sync_state FROM transactions WHERE id = '" + cinema.id() + "'")) {
            assertTrue(row.next());
            assertEquals(1, row.getInt("deleted"));
            assertEquals("PENDING", row.getString("sync_state"));
        }
    }

    @Test
    void deletingTwiceOrAnUnknownIdChangesNothing() throws SQLException {
        Transaction cinema = expense(2_800, "2026-10-12", "Cinema");
        repository.insert(cinema);
        repository.softDelete(cinema.id());

        assertFalse(repository.softDelete(cinema.id()));
        assertEquals(2, repository.findById(cinema.id()).orElseThrow().version());
        assertFalse(repository.softDelete("no-such-id"));
    }

    @Test
    void timestampsAreStoredWithAFixedWidth() throws SQLException {
        repository.insert(expense(500, "2026-10-25", "Coffee"));
        try (Connection connection = database.connect(); Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT created_at FROM transactions")) {
            assertTrue(row.next());
            assertEquals("2026-10-25T09:30:00.123Z", row.getString("created_at"));
        }
    }

    @Test
    void anUnknownCategoryIsRejectedByTheDatabase() {
        Transaction orphan = Transaction.create("no-such-category", TransactionType.EXPENSE, 500, "USD",
                LocalDate.parse("2026-10-25"), null, CREATED);
        assertThrows(SQLException.class, () -> repository.insert(orphan));
    }

    @Test
    void aTransactionCannotHaveAZeroOrNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> expense(0, "2026-10-25", "Nothing"));
        assertThrows(IllegalArgumentException.class, () -> expense(-500, "2026-10-25", "Refund?"));
    }
}
