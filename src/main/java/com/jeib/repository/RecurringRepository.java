package com.jeib.repository;

import com.jeib.db.Database;
import com.jeib.db.Timestamps;
import com.jeib.model.RecurringPayment;
import com.jeib.model.SupportedCurrency;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

public final class RecurringRepository {

    private final Database database;
    private final Clock clock;

    public RecurringRepository(Database database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    public void insert(RecurringPayment payment) throws SQLException {
        try (var connection = database.connect(); var statement = connection.prepareStatement("""
                INSERT INTO recurring_payments (id, name, category_id, amount, currency, day_of_month, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")) {
            String now = Timestamps.now(clock);
            statement.setString(1, payment.id());
            statement.setString(2, payment.name());
            statement.setString(3, payment.categoryId());
            statement.setLong(4, payment.amountMinor());
            statement.setString(5, payment.currency().name());
            statement.setInt(6, payment.dayOfMonth());
            statement.setString(7, now);
            statement.setString(8, now);
            statement.executeUpdate();
        }
    }

    public List<RecurringPayment> list(SupportedCurrency main) throws SQLException {
        try (var connection = database.connect(); var statement = connection.prepareStatement("""
                SELECT id, name, category_id, amount, currency, day_of_month
                FROM recurring_payments WHERE deleted = 0 AND active = 1 ORDER BY name, id""");
             var rows = statement.executeQuery()) {
            var payments = new ArrayList<RecurringPayment>();
            while (rows.next()) {
                String currency = rows.getString("currency");
                payments.add(new RecurringPayment(rows.getString("id"), rows.getString("name"),
                        rows.getString("category_id"), rows.getLong("amount"),
                        currency == null ? main : SupportedCurrency.valueOf(currency), rows.getInt("day_of_month")));
            }
            return List.copyOf(payments);
        }
    }

    public void archive(String id) throws SQLException {
        try (var connection = database.connect(); var statement = connection.prepareStatement("""
                UPDATE recurring_payments SET deleted = 1, updated_at = ?, version = version + 1
                WHERE id = ? AND deleted = 0""")) {
            statement.setString(1, Timestamps.now(clock));
            statement.setString(2, id);
            statement.executeUpdate();
        }
    }
}
