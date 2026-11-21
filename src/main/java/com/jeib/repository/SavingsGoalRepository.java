package com.jeib.repository;

import com.jeib.db.Database;
import com.jeib.db.Timestamps;
import com.jeib.model.SavingsGoal;
import com.jeib.model.SupportedCurrency;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

public final class SavingsGoalRepository {

    private final Database database;
    private final Clock clock;

    public SavingsGoalRepository(Database database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    public void insert(SavingsGoal goal) throws SQLException {
        try (var connection = database.connect(); var statement = connection.prepareStatement("""
                INSERT INTO savings_goals (id, name, target_amount, currency, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)""")) {
            String now = Timestamps.now(clock);
            statement.setString(1, goal.id());
            statement.setString(2, goal.name());
            statement.setLong(3, goal.targetMinor());
            statement.setString(4, goal.currency().name());
            statement.setString(5, now);
            statement.setString(6, now);
            statement.executeUpdate();
        }
    }

    public List<SavingsGoal> list() throws SQLException {
        try (var connection = database.connect(); var statement = connection.prepareStatement("""
                SELECT id, name, target_amount, saved_amount, currency
                FROM savings_goals WHERE deleted = 0 ORDER BY name, id""");
             var rows = statement.executeQuery()) {
            var goals = new ArrayList<SavingsGoal>();
            while (rows.next()) {
                goals.add(new SavingsGoal(rows.getString("id"), rows.getString("name"), rows.getLong("target_amount"),
                        rows.getLong("saved_amount"), SupportedCurrency.valueOf(rows.getString("currency"))));
            }
            return List.copyOf(goals);
        }
    }

    public boolean addSavings(String id, long amount, long maximum) throws SQLException {
        try (var connection = database.connect(); var statement = connection.prepareStatement("""
                UPDATE savings_goals SET saved_amount = saved_amount + ?, updated_at = ?, version = version + 1
                WHERE id = ? AND deleted = 0 AND saved_amount <= ?""")) {
            statement.setLong(1, amount);
            statement.setString(2, Timestamps.now(clock));
            statement.setString(3, id);
            statement.setLong(4, maximum - amount);
            return statement.executeUpdate() == 1;
        }
    }

    public void archive(String id) throws SQLException {
        try (var connection = database.connect(); var statement = connection.prepareStatement("""
                UPDATE savings_goals SET deleted = 1, updated_at = ?, version = version + 1
                WHERE id = ? AND deleted = 0""")) {
            statement.setString(1, Timestamps.now(clock));
            statement.setString(2, id);
            statement.executeUpdate();
        }
    }
}
