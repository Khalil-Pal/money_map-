package com.jeib.repository;

import com.jeib.db.Database;
import com.jeib.model.Category;
import com.jeib.model.TransactionType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class CategoryRepository {

    private final Database database;

    public CategoryRepository(Database database) {
        this.database = database;
    }

    /** Non-deleted categories: expenses first, then income, each in palette order. */
    public List<Category> findActive() throws SQLException {
        String sql = """
                SELECT id, name, kind, color_index, built_in
                FROM categories
                WHERE deleted = 0
                ORDER BY kind, color_index""";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            List<Category> categories = new ArrayList<>();
            while (rows.next()) {
                categories.add(new Category(
                        rows.getString("id"),
                        rows.getString("name"),
                        TransactionType.valueOf(rows.getString("kind")),
                        rows.getInt("color_index"),
                        rows.getInt("built_in") == 1));
            }
            return categories;
        }
    }
}
