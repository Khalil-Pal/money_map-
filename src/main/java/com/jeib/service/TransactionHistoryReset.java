package com.jeib.service;

import static com.jeib.i18n.Messages.t;

import com.jeib.db.Database;
import com.jeib.db.Timestamps;
import com.jeib.i18n.DefaultCategories;
import com.jeib.i18n.Messages;
import com.jeib.model.SupportedCurrency;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.StringJoiner;

/** A single confirmed operation that serializes with transaction commands and resets their undo history. */
public final class TransactionHistoryReset {

    private final Database database;
    private final UndoManager history;
    private final Clock clock;

    public TransactionHistoryReset(Database database, UndoManager history, Clock clock) {
        this.database = database;
        this.history = history;
        this.clock = clock;
    }

    /** A null export means the user chose to delete without a PDF. */
    public int execute(Path export) throws SQLException, IOException {
        synchronized (history) {
            try (var connection = database.connect(); var control = connection.createStatement()) {
                // Reserve the write lock before reading. Every active transaction exported is cleared in this transaction.
                control.execute("BEGIN IMMEDIATE");
                try {
                    if (export != null) {
                        writeExport(connection, export.toAbsolutePath().normalize());
                    }
                    int count;
                    try (var update = connection.prepareStatement("""
                            UPDATE transactions
                            SET deleted = 1, version = version + 1, updated_at = ?, sync_state = 'PENDING'
                            WHERE deleted = 0""")) {
                        update.setString(1, Timestamps.now(clock));
                        count = update.executeUpdate();
                    }
                    control.execute("COMMIT");
                    history.clear();
                    return count;
                } catch (SQLException | IOException | RuntimeException error) {
                    try {
                        control.execute("ROLLBACK");
                    } catch (SQLException rollbackError) {
                        error.addSuppressed(rollbackError);
                    }
                    throw error;
                }
            }
        }
    }

    private void writeExport(Connection connection, Path destination) throws IOException, SQLException {
        if (destination.getFileName() == null
                || !destination.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IOException(t("Choose a file ending in .pdf."));
        }
        Path temporary = Files.createTempFile(destination.getParent(), ".jeib-export-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary); var pdf = new HistoryPdf(output, Messages.rightToLeft())) {
                pdf.heading(t("Jeib data export"));
                pdf.row(Messages.ltr(LocalDate.now(clock).toString()));
                section(connection, pdf, t("Transactions"), """
                        SELECT t.occurred_on, t.type, t.amount, t.currency, t.note,
                               c.id AS category_id, c.name AS category_name, p.name AS plan, a.name AS account
                        FROM transactions t JOIN categories c ON c.id = t.category_id
                        LEFT JOIN budget_plans p ON p.id = t.budget_plan_id
                        LEFT JOIN accounts a ON a.id = t.account_id
                        WHERE t.deleted = 0 ORDER BY t.occurred_on, t.created_at, t.id
                        """, row -> {
                    String line = t("{0} · {1} · {2} · {3}", Messages.ltr(row.getString("occurred_on")),
                            kind(row.getString("type")), amount(row, "amount", "currency"), category(row));
                    if (row.getString("plan") != null) {
                        line += "\n" + t("Budget plan: {0}", row.getString("plan"));
                    }
                    if (row.getString("account") != null) {
                        line += "\n" + t("Account: {0}", row.getString("account"));
                    }
                    if (row.getString("note") != null) {
                        line += "\n" + t("Note: {0}", row.getString("note"));
                    }
                    return line;
                });
                section(connection, pdf, t("Budget plans"), """
                        SELECT p.name, p.start_date, p.end_date, p.limit_amount, p.currency, p.id
                        FROM budget_plans p WHERE deleted = 0 ORDER BY start_date, name, id
                        """, row -> t("{0} · {1} · {2}", row.getString("name"),
                        t("{0} to {1}", Messages.ltr(row.getString("start_date")), Messages.ltr(row.getString("end_date"))),
                        amount(row, "limit_amount", "currency"))
                        + "\n" + t("Categories: {0}", planCategories(connection, row.getString("id"))));
                section(connection, pdf, t("Monthly limits"), """
                        SELECT b.month, b.limit_amount, c.id AS category_id, c.name AS category_name,
                               (SELECT value FROM settings WHERE key = 'main_currency') AS currency
                        FROM budgets b LEFT JOIN categories c ON c.id = b.category_id
                        WHERE b.deleted = 0 ORDER BY b.month, c.name
                        """, row -> t("{0} · {1} · {2}",
                        row.getString("category_id") == null ? t("All spending") : category(row),
                        row.getString("month") == null ? t("Every month") : Messages.ltr(row.getString("month")),
                        amount(row, "limit_amount", "currency")));
                section(connection, pdf, t("Categories"), """
                        SELECT id AS category_id, name AS category_name, kind
                        FROM categories WHERE deleted = 0 ORDER BY kind, name
                        """, row -> t("{0} · {1}", category(row), kind(row.getString("kind"))));
                section(connection, pdf, t("Recurring payments"), """
                        SELECT r.name, r.day_of_month, r.amount, r.note, c.id AS category_id, c.name AS category_name,
                               coalesce(r.currency, (SELECT value FROM settings WHERE key = 'main_currency')) AS currency
                        FROM recurring_payments r JOIN categories c ON c.id = r.category_id
                        WHERE r.deleted = 0 AND r.active = 1 ORDER BY r.day_of_month, r.name
                        """, row -> t("{0} · {1} · {2} · {3}",
                        row.getString("name") == null ? category(row) : row.getString("name"),
                        category(row), t("Day {0} of each month", row.getInt("day_of_month")),
                        amount(row, "amount", "currency"))
                        + (row.getString("note") == null ? "" : "\n" + t("Note: {0}", row.getString("note"))));
                section(connection, pdf, t("Savings goals"), """
                        SELECT name, target_amount, saved_amount, currency
                        FROM savings_goals WHERE deleted = 0 ORDER BY name, id
                        """, row -> t("{0} · {1}", row.getString("name"),
                        t("{0} saved of {1}", amount(row, "saved_amount", "currency"), amount(row, "target_amount", "currency"))));
                section(connection, pdf, t("Exchange rates"), """
                        SELECT substr(key, 6) AS currency, value,
                               (SELECT value FROM settings WHERE key = 'main_currency') AS main
                        FROM settings WHERE key LIKE 'rate.%' ORDER BY key
                        """, row -> t("1 {0} = {1} {2}", row.getString("currency"), row.getString("value"), row.getString("main")));
            }
            // The report must exist at its final path and be fully closed before changing any rows.
            try {
                Files.move(temporary, destination);
            } catch (FileAlreadyExistsException error) {
                throw new IOException(t("A file with that name already exists. Choose a new filename."), error);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String planCategories(Connection connection, String id) throws SQLException {
        var names = new StringJoiner(", ");
        try (var statement = connection.prepareStatement("""
                SELECT c.id AS category_id, c.name AS category_name
                FROM budget_plan_categories pc JOIN categories c ON c.id = pc.category_id
                WHERE pc.plan_id = ? ORDER BY c.name""")) {
            statement.setString(1, id);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    names.add(category(rows));
                }
            }
        }
        return names.toString();
    }

    private static String category(ResultSet row) throws SQLException {
        return DefaultCategories.displayName(row.getString("category_id"), row.getString("category_name"));
    }

    private static String kind(String kind) {
        return "INCOME".equals(kind) ? t("Income") : t("Expense");
    }

    private static String amount(ResultSet row, String field, String currencyField) throws SQLException {
        long value = row.getLong(field);
        String currency = row.getString(currencyField);
        return currency == null ? BigDecimal.valueOf(value, 2).toPlainString()
                : SupportedCurrency.valueOf(currency).format(value);
    }

    private static void section(Connection connection, HistoryPdf pdf, String heading, String sql, RowText format)
            throws SQLException, IOException {
        pdf.heading(heading);
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            if (!rows.next()) {
                pdf.row(t("No entries."));
                return;
            }
            do {
                pdf.row(format.text(rows));
            } while (rows.next());
        }
    }

    @FunctionalInterface
    private interface RowText {
        String text(ResultSet row) throws SQLException;
    }
}
