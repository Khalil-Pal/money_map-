package com.jeib.repository;

import com.jeib.db.Database;
import com.jeib.db.Timestamps;
import com.jeib.model.Category;
import com.jeib.model.TransactionType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQL for the categories table. Business rules (names, colors, built-ins) live in CategoryService. */
public final class CategoryRepository {

    private static final String COLUMNS = "id, name, kind, color_index, built_in";

    private final Database database;
    private final Clock clock;

    public CategoryRepository(Database database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    /** Non-deleted categories: expenses first, then income, each in palette order. */
    public List<Category> findActive() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM categories WHERE deleted = 0 ORDER BY kind, color_index";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            List<Category> categories = new ArrayList<>();
            while (rows.next()) {
                categories.add(map(rows));
            }
            return categories;
        }
    }

    public Optional<Category> findActiveById(String id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM categories WHERE id = ? AND deleted = 0";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    /** "Other" for EXPENSE, "Other income" for INCOME: the fallback when a category is deleted. */
    public Category findBuiltIn(TransactionType kind) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM categories WHERE kind = ? AND built_in = 1 AND deleted = 0";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, kind.name());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("The built-in " + kind + " category is missing");
                }
                return map(rows);
            }
        }
    }

    /**
     * Whether another live category of this kind already has the name, ignoring case.
     * Mirrors the unique index ux_categories_kind_name, so the service can explain the problem
     * before the database refuses the row.
     *
     * @param exceptId the category being renamed (it may keep its own name), or null when creating
     */
    public boolean nameTaken(TransactionType kind, String name, String exceptId) throws SQLException {
        String sql = """
                SELECT 1 FROM categories
                WHERE deleted = 0 AND kind = ? AND name = ? COLLATE NOCASE AND id <> ?
                LIMIT 1""";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, kind.name());
            statement.setString(2, name);
            statement.setString(3, exceptId == null ? "" : exceptId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    /** How many live categories use each palette color; index = color index. */
    public int[] colorUsage() throws SQLException {
        String sql = "SELECT color_index, COUNT(*) AS uses FROM categories WHERE deleted = 0 GROUP BY color_index";
        int[] usage = new int[Category.PALETTE_SIZE];
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                usage[rows.getInt("color_index")] = rows.getInt("uses");
            }
        }
        return usage;
    }

    public void insert(Category category) throws SQLException {
        String sql = """
                INSERT INTO categories (id, name, kind, color_index, built_in, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""";
        String now = Timestamps.now(clock);
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category.id());
            statement.setString(2, category.name());
            statement.setString(3, category.kind().name());
            statement.setInt(4, category.colorIndex());
            statement.setInt(5, category.builtIn() ? 1 : 0);
            statement.setString(6, now);
            statement.setString(7, now);
            statement.executeUpdate();
        }
    }

    /**
     * Renames and/or recolors a live category, bumping version and updated_at for sync.
     *
     * @return false if there was no live category with that id
     */
    public boolean update(String id, String name, int colorIndex) throws SQLException {
        String sql = """
                UPDATE categories
                SET name = ?, color_index = ?, version = version + 1, updated_at = ?
                WHERE id = ? AND deleted = 0""";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setInt(2, colorIndex);
            statement.setString(3, Timestamps.now(clock));
            statement.setString(4, id);
            return statement.executeUpdate() == 1;
        }
    }

    /**
     * Soft-deletes a category and moves its live transactions to {@code fallbackId}, all in one
     * database transaction: either both happen or neither does, so no transaction is ever left
     * pointing at a deleted category. Moved transactions get a new version and updated_at and are
     * marked for the next sync push.
     *
     * @return ids of the moved transactions (undo will need them to move them back)
     * @throws IllegalArgumentException if the category doesn't exist, is already deleted, or is built in
     */
    public List<String> softDelete(String id, String fallbackId) throws SQLException {
        String now = Timestamps.now(clock);
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                List<String> moved = new ArrayList<>();
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT id FROM transactions WHERE category_id = ? AND deleted = 0")) {
                    select.setString(1, id);
                    try (ResultSet rows = select.executeQuery()) {
                        while (rows.next()) {
                            moved.add(rows.getString("id"));
                        }
                    }
                }
                try (PreparedStatement move = connection.prepareStatement("""
                        UPDATE transactions
                        SET category_id = ?, version = version + 1, updated_at = ?, sync_state = 'PENDING'
                        WHERE category_id = ? AND deleted = 0""")) {
                    move.setString(1, fallbackId);
                    move.setString(2, now);
                    move.setString(3, id);
                    move.executeUpdate();
                }
                int deleted;
                try (PreparedStatement delete = connection.prepareStatement("""
                        UPDATE categories
                        SET deleted = 1, version = version + 1, updated_at = ?
                        WHERE id = ? AND deleted = 0 AND built_in = 0""")) {
                    delete.setString(1, now);
                    delete.setString(2, id);
                    deleted = delete.executeUpdate();
                }
                if (deleted != 1) {
                    // Undo the transaction moves above too.
                    throw new IllegalArgumentException("No deletable category with id " + id);
                }
                connection.commit();
                return moved;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static Category map(ResultSet row) throws SQLException {
        return new Category(
                row.getString("id"),
                row.getString("name"),
                TransactionType.valueOf(row.getString("kind")),
                row.getInt("color_index"),
                row.getInt("built_in") == 1);
    }
}
