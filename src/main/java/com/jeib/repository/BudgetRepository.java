package com.jeib.repository;

import com.jeib.db.Database;
import com.jeib.db.Timestamps;
import com.jeib.model.Budget;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQL for the budgets table. For now every limit is a default for every month ({@code month IS
 * NULL}); limits for one specific month can be added later without a schema change.
 */
public final class BudgetRepository {

    private final Database database;
    private final Clock clock;

    public BudgetRepository(Database database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    /** Live default limits: the overall one (category NULL) and one per category at most. */
    public List<Budget> findDefaults() throws SQLException {
        String sql = "SELECT id, category_id, limit_amount FROM budgets WHERE deleted = 0 AND month IS NULL";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            List<Budget> budgets = new ArrayList<>();
            while (rows.next()) {
                budgets.add(map(rows));
            }
            return budgets;
        }
    }

    /** @param categoryId null for the overall limit ({@code IS} matches NULL, unlike {@code =}) */
    public Optional<Budget> findDefault(String categoryId) throws SQLException {
        String sql = """
                SELECT id, category_id, limit_amount FROM budgets
                WHERE deleted = 0 AND month IS NULL AND category_id IS ?""";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, categoryId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    public void insert(Budget budget) throws SQLException {
        String sql = """
                INSERT INTO budgets (id, category_id, month, limit_amount, created_at, updated_at)
                VALUES (?, ?, NULL, ?, ?, ?)""";
        String now = Timestamps.now(clock);
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, budget.id());
            statement.setString(2, budget.categoryId());
            statement.setLong(3, budget.limitMinor());
            statement.setString(4, now);
            statement.setString(5, now);
            statement.executeUpdate();
        }
    }

    /** @return false if there was no live limit with that id */
    public boolean updateLimit(String id, long limitMinor) throws SQLException {
        String sql = """
                UPDATE budgets SET limit_amount = ?, version = version + 1, updated_at = ?
                WHERE id = ? AND deleted = 0""";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, limitMinor);
            statement.setString(2, Timestamps.now(clock));
            statement.setString(3, id);
            return statement.executeUpdate() == 1;
        }
    }

    /** Removing a limit is a soft delete, like every synced row. */
    public boolean softDelete(String id) throws SQLException {
        String sql = """
                UPDATE budgets SET deleted = 1, version = version + 1, updated_at = ?
                WHERE id = ? AND deleted = 0""";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, Timestamps.now(clock));
            statement.setString(2, id);
            return statement.executeUpdate() == 1;
        }
    }

    private static Budget map(ResultSet row) throws SQLException {
        return new Budget(row.getString("id"), row.getString("category_id"), row.getLong("limit_amount"));
    }
}
