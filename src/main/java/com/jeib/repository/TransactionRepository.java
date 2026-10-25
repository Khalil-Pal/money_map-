package com.jeib.repository;

import com.jeib.db.Database;
import com.jeib.db.Timestamps;
import com.jeib.model.Transaction;
import com.jeib.model.TransactionType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TransactionRepository {

    private static final String COLUMNS = """
            id, account_id, category_id, type, amount, currency, occurred_on, note, created_at, updated_at, version""";

    private final Database database;
    private final Clock clock;

    public TransactionRepository(Database database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    public void insert(Transaction t) throws SQLException {
        String sql = "INSERT INTO transactions (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            statement.executeUpdate();
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
        // Dates are stored as YYYY-MM-DD text, which sorts like a date, so a half-open range
        // [first day, first day of next month) selects the month and can use idx_tx_date.
        String sql = "SELECT " + COLUMNS + """
                 FROM transactions
                WHERE deleted = 0 AND occurred_on >= ? AND occurred_on < ?
                ORDER BY occurred_on DESC, created_at DESC""";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, month.atDay(1).toString());
            statement.setString(2, month.plusMonths(1).atDay(1).toString());
            try (ResultSet rows = statement.executeQuery()) {
                List<Transaction> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(map(rows));
                }
                return result;
            }
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
            statement.setString(1, Timestamps.format(clock.instant().truncatedTo(ChronoUnit.MILLIS)));
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
                row.getInt("version"));
    }
}
