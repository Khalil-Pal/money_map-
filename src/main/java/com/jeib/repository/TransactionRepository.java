package com.jeib.repository;

import com.jeib.db.Database;
import com.jeib.db.Timestamps;
import com.jeib.model.DateRange;
import com.jeib.model.Transaction;
import com.jeib.model.TransactionType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TransactionRepository {

    private static final String COLUMNS = """
            id, account_id, category_id, type, amount, currency, occurred_on, note, created_at, updated_at, version, budget_plan_id""";

    private final Database database;
    private final Clock clock;

    public TransactionRepository(Database database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    public void insert(Transaction t) throws SQLException {
        String sql = "INSERT INTO transactions (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, t.id());
            statement.setString(2, t.accountId());
            statement.setString(3, t.categoryId());
            statement.setString(4, t.type().name());
            statement.setLong(5, t.amountMinor());
            statement.setString(6, t.currency());
            statement.setString(7, t.occurredOn().toString());
            statement.setString(8, t.note());
            statement.setString(9, Timestamps.format(t.createdAt()));
            statement.setString(10, Timestamps.format(t.updatedAt()));
            statement.setInt(11, t.version());
            statement.setString(12, t.budgetPlanId());
            statement.executeUpdate();
        }
    }

    /**
     * Saves an edited transaction. {@code t} must carry the next version (stored version + 1) and
     * its new updated_at; the row is marked for the next sync push.
     *
     * <p>Optimistic locking: the WHERE clause only matches if the stored version is still
     * {@code t.version() - 1}. If something else changed the row in between (later: a sync pull),
     * nothing is overwritten and this returns false.
     *
     * @return false if the transaction is deleted, missing, or was changed in the meantime
     */
    public boolean update(Transaction t) throws SQLException {
        String sql = """
                UPDATE transactions
                SET account_id = ?, category_id = ?, type = ?, amount = ?, currency = ?, occurred_on = ?, note = ?,
                    updated_at = ?, version = ?, budget_plan_id = ?, sync_state = 'PENDING'
                WHERE id = ? AND deleted = 0 AND version = ?""";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, t.accountId());
            statement.setString(2, t.categoryId());
            statement.setString(3, t.type().name());
            statement.setLong(4, t.amountMinor());
            statement.setString(5, t.currency());
            statement.setString(6, t.occurredOn().toString());
            statement.setString(7, t.note());
            statement.setString(8, Timestamps.format(t.updatedAt()));
            statement.setInt(9, t.version());
            statement.setString(10, t.budgetPlanId());
            statement.setString(11, t.id());
            statement.setInt(12, t.version() - 1);
            return statement.executeUpdate() == 1;
        }
    }

    /** Includes soft-deleted rows, so undo and sync can still see them. */
    public Optional<Transaction> findById(String id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM transactions WHERE id = ?";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    /** Live transactions in the month, newest first. */
    public List<Transaction> findByMonth(YearMonth month) throws SQLException {
        return findByRange(DateRange.ofMonth(month));
    }

    /** Live transactions, including both dates, newest first. */
    public List<Transaction> findByRange(DateRange range) throws SQLException {
        return findByRange(range, null);
    }

    public List<Transaction> findByRange(DateRange range, String budgetPlanId) throws SQLException {
        // YYYY-MM-DD sorts by date, so SQLite can use idx_tx_date for both bounds.
        String sql = "SELECT " + COLUMNS + """
                 FROM transactions
                WHERE deleted = 0 AND occurred_on >= ? AND occurred_on <= ?
                """ + (budgetPlanId == null ? "" : " AND budget_plan_id = ?")
                + " ORDER BY occurred_on DESC, created_at DESC";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, range.start().toString());
            statement.setString(2, range.end().toString());
            if (budgetPlanId != null) {
                statement.setString(3, budgetPlanId);
            }
            try (ResultSet rows = statement.executeQuery()) {
                List<Transaction> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(map(rows));
                }
                return result;
            }
        }
    }

    public Optional<YearMonth> firstRecordedMonth() throws SQLException {
        try (var connection = database.connect();
             var statement = connection.prepareStatement("SELECT MIN(occurred_on) FROM transactions");
             var rows = statement.executeQuery()) {
            String date = rows.next() ? rows.getString(1) : null;
            return Optional.ofNullable(date).map(LocalDate::parse).map(YearMonth::from);
        }
    }

    /**
     * Marks the transaction deleted instead of removing the row, so the deletion can be undone and
     * synced to other devices. Bumps version and updated_at and marks it for the next sync push.
     *
     * @return false if there was no live transaction with that id
     */
    public boolean softDelete(String id) throws SQLException {
        String sql = """
                UPDATE transactions
                SET deleted = 1, version = version + 1, updated_at = ?, sync_state = 'PENDING'
                WHERE id = ? AND deleted = 0""";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, Timestamps.now(clock));
            statement.setString(2, id);
            return statement.executeUpdate() == 1;
        }
    }

    /** The sum of one category's expenses in one currency (minor units of that currency). */
    public record Spend(String categoryId, String currency, long amountMinor) {
    }

    /**
     * Live EXPENSE totals in the date range, per category and currency. Summed in SQL so the database
     * does the work (idx_tx_date covers the range); converting to the main currency is the
     * service's job, once per currency rather than once per row.
     */
    public List<Spend> sumExpensesByCategory(DateRange range) throws SQLException {
        String sql = """
                SELECT category_id, currency, SUM(amount) AS total
                FROM transactions
                WHERE deleted = 0 AND type = 'EXPENSE' AND occurred_on >= ? AND occurred_on <= ?
                GROUP BY category_id, currency""";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, range.start().toString());
            statement.setString(2, range.end().toString());
            try (ResultSet rows = statement.executeQuery()) {
                List<Spend> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(new Spend(rows.getString("category_id"), rows.getString("currency"),
                            rows.getLong("total")));
                }
                return result;
            }
        }
    }

    /**
     * Brings back a soft-deleted transaction (undo of a delete). Like every change it bumps version
     * and updated_at and marks the row for the next sync push, so other devices see it come back.
     *
     * @return false if there was no deleted transaction with that id
     */
    public boolean restore(String id) throws SQLException {
        String sql = """
                UPDATE transactions
                SET deleted = 0, version = version + 1, updated_at = ?, sync_state = 'PENDING'
                WHERE id = ? AND deleted = 1""";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, Timestamps.now(clock));
            statement.setString(2, id);
            return statement.executeUpdate() == 1;
        }
    }

    private static Transaction map(ResultSet row) throws SQLException {
        return new Transaction(
                row.getString("id"),
                row.getString("account_id"),
                row.getString("category_id"),
                TransactionType.valueOf(row.getString("type")),
                row.getLong("amount"),
                row.getString("currency"),
                LocalDate.parse(row.getString("occurred_on")),
                row.getString("note"),
                Timestamps.parse(row.getString("created_at")),
                Timestamps.parse(row.getString("updated_at")),
                row.getInt("version"),
                row.getString("budget_plan_id"));
    }
}
