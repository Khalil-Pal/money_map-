package com.jeib.repository;

import com.jeib.db.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The key/value settings table (main currency, exchange rates, later theme etc.). Not synced. */
public final class SettingsRepository {

    private final Database database;

    public SettingsRepository(Database database) {
        this.database = database;
    }

    public Optional<String> get(String key) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            statement.setString(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(rows.getString("value")) : Optional.empty();
            }
        }
    }

    /** Every setting whose key starts with {@code prefix}, e.g. "rate." → {"rate.USD": "92.5"}. */
    public Map<String, String> findByPrefix(String prefix) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT key, value FROM settings WHERE substr(key, 1, length(?)) = ?")) {
            statement.setString(1, prefix);
            statement.setString(2, prefix);
            try (ResultSet rows = statement.executeQuery()) {
                Map<String, String> result = new HashMap<>();
                while (rows.next()) {
                    result.put(rows.getString("key"), rows.getString("value"));
                }
                return result;
            }
        }
    }

    /**
     * Writes several settings in one database transaction: all of them change or none do.
     * A null value removes the key.
     */
    public void save(Map<String, String> changes) throws SQLException {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement upsert = connection.prepareStatement("""
                         INSERT INTO settings (key, value) VALUES (?, ?)
                         ON CONFLICT (key) DO UPDATE SET value = excluded.value""");
                 PreparedStatement delete = connection.prepareStatement("DELETE FROM settings WHERE key = ?")) {
                for (Map.Entry<String, String> change : changes.entrySet()) {
                    if (change.getValue() == null) {
                        delete.setString(1, change.getKey());
                        delete.executeUpdate();
                    } else {
                        upsert.setString(1, change.getKey());
                        upsert.setString(2, change.getValue());
                        upsert.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }
}
