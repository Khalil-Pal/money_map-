package com.jeib.ui;

import com.jeib.model.Category;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.Transaction;
import com.jeib.model.TransactionType;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One line of the Transactions list, already formatted for display. Built off the JavaFX thread
 * from plain data, so it's easy to test without starting the UI.
 *
 * @param note          the user's note, or null (the list then shows a muted "No note")
 * @param colorIndex    the category's palette color, for its swatch
 * @param amount        e.g. "$12.50" for an expense, "+950.00 ₽" for income
 */
public record TransactionRow(
        String id,
        String date,
        String note,
        String categoryName,
        int colorIndex,
        String amount,
        boolean income) {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);
    static final String UNKNOWN_CATEGORY = "Unknown category";
    /** Slate, the same neutral color as "Other". */
    static final int UNKNOWN_COLOR = 6;

    /** Turns transactions into rows, in the same order. Categories are looked up by id. */
    public static List<TransactionRow> from(List<Transaction> transactions, List<Category> categories) {
        Map<String, Category> byId = categories.stream().collect(Collectors.toMap(Category::id, Function.identity()));
        return transactions.stream().map(t -> from(t, byId.get(t.categoryId()))).toList();
    }

    private static TransactionRow from(Transaction t, Category category) {
        boolean income = t.type() == TransactionType.INCOME;
        return new TransactionRow(
                t.id(),
                DATE.format(t.occurredOn()),
                t.note(),
                category == null ? UNKNOWN_CATEGORY : category.name(),
                category == null ? UNKNOWN_COLOR : category.colorIndex(),
                (income ? "+" : "") + formatAmount(t.amountMinor(), t.currency()),
                income);
    }

    private static String formatAmount(long minorUnits, String currencyCode) {
        try {
            return SupportedCurrency.valueOf(currencyCode).format(minorUnits);
        } catch (IllegalArgumentException e) {
            // Not one of ours (shouldn't happen): show the plain number and the code.
            return SupportedCurrency.USD.format(minorUnits).substring(1) + " " + currencyCode;
        }
    }
}
