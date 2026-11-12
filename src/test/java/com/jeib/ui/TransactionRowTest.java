package com.jeib.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.model.Category;
import com.jeib.model.Transaction;
import com.jeib.model.TransactionType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionRowTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);
    private static final Category FOOD = new Category("food", "Food", TransactionType.EXPENSE, 0, false);
    private static final Category SALARY = new Category("salary", "Salary", TransactionType.INCOME, 8, false);

    private static Transaction transaction(String categoryId, TransactionType type, long cents, String currency,
                                           String date, String note) {
        return Transaction.create(categoryId, type, cents, currency, LocalDate.parse(date), note, CLOCK);
    }

    @Test
    void anExpenseRowShowsDateNoteCategoryAndAmount() {
        TransactionRow row = TransactionRow.from(
                List.of(transaction("food", TransactionType.EXPENSE, 1_250, "USD", "2026-11-12", "Lunch")),
                List.of(FOOD, SALARY)).getFirst();

        assertEquals("Thu, Nov 12", row.date());
        assertEquals("Lunch", row.note());
        assertEquals("Food", row.categoryName());
        assertEquals(0, row.colorIndex());
        assertEquals("$12.50", row.amount());
        assertFalse(row.income());
    }

    @Test
    void incomeGetsAPlusSignAndKeepsItsOwnCurrency() {
        TransactionRow row = TransactionRow.from(
                List.of(transaction("salary", TransactionType.INCOME, 2_500_000, "RUB", "2026-11-01", null)),
                List.of(FOOD, SALARY)).getFirst();

        assertEquals("+25,000.00 ₽", row.amount());
        assertTrue(row.income());
        assertNull(row.note());
    }

    @Test
    void rowsKeepTheOrderTheyWereGiven() {
        List<TransactionRow> rows = TransactionRow.from(List.of(
                transaction("food", TransactionType.EXPENSE, 100, "ILS", "2026-11-12", "second"),
                transaction("food", TransactionType.EXPENSE, 200, "ILS", "2026-11-03", "first")),
                List.of(FOOD));

        assertEquals(List.of("second", "first"), rows.stream().map(TransactionRow::note).toList());
    }

    @Test
    void aMissingCategoryStillShowsARow() {
        TransactionRow row = TransactionRow.from(
                List.of(transaction("gone", TransactionType.EXPENSE, 100, "USD", "2026-11-12", null)),
                List.of(FOOD)).getFirst();

        assertEquals(TransactionRow.UNKNOWN_CATEGORY, row.categoryName());
        assertEquals(TransactionRow.UNKNOWN_COLOR, row.colorIndex());
    }
}
