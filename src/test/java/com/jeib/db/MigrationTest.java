package com.jeib.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.model.Category;
import com.jeib.model.TransactionType;
import com.jeib.repository.CategoryRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the real Flyway migrations against a throwaway SQLite file. */
class MigrationTest {

    @TempDir
    Path tempDir;

    private Database database;

    @BeforeEach
    void migrate() {
        database = new Database(tempDir.resolve("test.db"));
        database.migrate();
    }

    @Test
    void seedsTheDefaultCategories() throws SQLException {
        List<Category> categories = new CategoryRepository(database).findActive();

        assertEquals(11, categories.size());
        assertEquals(8, categories.stream().filter(c -> c.kind() == TransactionType.EXPENSE).count());
        Set<String> builtIn = categories.stream().filter(Category::builtIn).map(Category::name).collect(Collectors.toSet());
        assertEquals(Set.of("Other", "Other income"), builtIn);
    }

    @Test
    void everyDefaultCategoryHasItsOwnColor() throws SQLException {
        List<Category> categories = new CategoryRepository(database).findActive();
        long distinctColors = categories.stream().mapToInt(Category::colorIndex).distinct().count();
        assertEquals(categories.size(), distinctColors);
        assertTrue(categories.stream().allMatch(c -> c.colorIndex() >= 0 && c.colorIndex() <= 13));
    }

    @Test
    void migratingTwiceIsANoOp() throws SQLException {
        database.migrate();
        assertEquals(11, new CategoryRepository(database).findActive().size());
    }

    @Test
    void categoryNamesAreUniquePerKindIgnoringCase() throws SQLException {
        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO categories (id, name, kind, color_index, created_at, updated_at)
                    VALUES ('x', 'food', 'EXPENSE', 10, '2026-10-25T00:00:00Z', '2026-10-25T00:00:00Z')"""));
            // The same name as an income category is fine.
            statement.executeUpdate("""
                    INSERT INTO categories (id, name, kind, color_index, created_at, updated_at)
                    VALUES ('y', 'Food', 'INCOME', 10, '2026-10-25T00:00:00Z', '2026-10-25T00:00:00Z')""");
        }
    }

    @Test
    void transactionAmountsMustBePositiveAndReferenceACategory() throws SQLException {
        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            String insert = """
                    INSERT INTO transactions (id, category_id, type, amount, currency, occurred_on, created_at, updated_at)
                    VALUES ('%s', '%s', 'EXPENSE', %d, 'USD', '2026-10-25', '2026-10-25T00:00:00Z', '2026-10-25T00:00:00Z')""";
            String food = "a87a6066-c95c-48d4-b8c7-849a88bcab1f";
            statement.executeUpdate(insert.formatted("t1", food, 1250));
            assertThrows(SQLException.class, () -> statement.executeUpdate(insert.formatted("t2", food, 0)));
            assertThrows(SQLException.class, () -> statement.executeUpdate(insert.formatted("t3", "no-such-category", 500)));
        }
    }
}
