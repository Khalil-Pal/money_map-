package com.jeib.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.db.Database;
import com.jeib.model.Budget;
import com.jeib.model.LimitStatus;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.Transaction;
import com.jeib.model.TransactionType;
import com.jeib.repository.BudgetRepository;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.SettingsRepository;
import com.jeib.repository.TransactionRepository;
import com.jeib.service.BudgetService.LimitLine;
import com.jeib.service.BudgetService.MonthBudget;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Limits against a real database: sums, conversion, statuses at the edges, set/remove. */
class BudgetServiceTest {

    private static final String FOOD = "a87a6066-c95c-48d4-b8c7-849a88bcab1f";
    private static final String RENT = "baa5872b-84d2-4d48-b6f0-a5bd9051e341";
    private static final String SALARY = "ee0d97a3-17cf-4704-ad63-4fe0a9cd69bb";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    @TempDir
    Path tempDir;

    private CurrencyService currencies;
    private TransactionService transactions;
    private BudgetRepository budgetRepository;
    private BudgetService budgets;

    @BeforeEach
    void setUp() throws SQLException {
        Database database = new Database(tempDir.resolve("test.db"));
        database.migrate();
        currencies = new CurrencyService(new SettingsRepository(database));
        currencies.setMainCurrency(SupportedCurrency.RUB);
        currencies.setRate(SupportedCurrency.USD, "92.5");
        CategoryRepository categories = new CategoryRepository(database, CLOCK);
        TransactionRepository transactionRepository = new TransactionRepository(database, CLOCK);
        transactions = new TransactionService(transactionRepository, categories, currencies, CLOCK);
        budgetRepository = new BudgetRepository(database, CLOCK);
        budgets = new BudgetService(budgetRepository, transactionRepository, categories, currencies);
    }

    private void spend(String amount, SupportedCurrency currency, String date, String categoryId) throws SQLException {
        transactions.add(new TransactionInput(TransactionType.EXPENSE, amount, currency, LocalDate.parse(date),
                categoryId, null));
    }

    private LimitLine food() throws SQLException {
        return budgets.forMonth(NOVEMBER).categories().stream()
                .filter(line -> line.category().id().equals(FOOD)).findFirst().orElseThrow();
    }

    @Test
    void withoutLimitsEverythingIsNoLimitButSpendingIsCounted() throws SQLException {
        spend("100", null, "2026-11-03", FOOD);
        spend("250.50", null, "2026-11-10", FOOD);
        spend("18000", null, "2026-11-01", RENT);

        MonthBudget month = budgets.forMonth(NOVEMBER);

        assertEquals(SupportedCurrency.RUB, month.mainCurrency());
        assertEquals(LimitStatus.NO_LIMIT, month.overall().status());
        assertEquals(1_835_050, month.overall().spentMinor());
        assertEquals(35_050, food().spentMinor());
        assertNull(food().limitMinor());
        assertNull(food().remainingMinor());
        assertEquals(8, month.categories().size()); // every expense category, income ones left out
    }

    @Test
    void onlyThisMonthsLiveExpensesCount() throws SQLException {
        spend("100", null, "2026-10-31", FOOD); // last day before
        spend("200", null, "2026-11-01", FOOD); // first day
        spend("300", null, "2026-11-30", FOOD); // last day
        spend("400", null, "2026-12-01", FOOD); // first day after
        transactions.add(new TransactionInput(TransactionType.INCOME, "5000", null, LocalDate.parse("2026-11-05"),
                SALARY, null));
        Transaction deleted = transactions.add(new TransactionInput(TransactionType.EXPENSE, "999", null,
                LocalDate.parse("2026-11-05"), FOOD, null));
        transactions.delete(deleted.id());

        assertEquals(50_000, food().spentMinor());
    }

    @Test
    void otherCurrenciesAreConvertedOncePerCurrency() throws SQLException {
        spend("12.50", SupportedCurrency.USD, "2026-11-05", FOOD); // 1156.25 RUB
        spend("100", null, "2026-11-05", FOOD);

        assertEquals(125_625, food().spentMinor());
    }

    @ParameterizedTest(name = "{0} of 2000.00 is {1} ({2}%)")
    @CsvSource({
            "1599.99, OK,       79",   // 79.9995%: still OK, and shown as 79% so it doesn't look like 80
            "1600,    WARNING,  80",   // exactly 80%
            "1999.99, WARNING,  99",   // shown as 99%, not 100%
            "2000,    EXCEEDED, 100",  // exactly 100%
            "2500,    EXCEEDED, 125",
    })
    void statusAtTheEdges(String spent, LimitStatus status, int percent) throws SQLException {
        budgets.setLimit(FOOD, "2000");
        spend(spent, null, "2026-11-05", FOOD);

        assertEquals(status, food().status());
        assertEquals(percent, food().percent());
    }

    @Test
    void theOverallLimitCoversAllCategories() throws SQLException {
        budgets.setLimit(null, "20000");
        spend("1000", null, "2026-11-05", FOOD);
        spend("15000", null, "2026-11-01", RENT);

        LimitLine overall = budgets.forMonth(NOVEMBER).overall();

        assertTrue(overall.isOverall());
        assertEquals(LimitStatus.WARNING, overall.status());
        assertEquals(400_000, overall.remainingMinor());
    }

    @Test
    void settingALimitAgainChangesItAndRemovingItWorks() throws SQLException {
        budgets.setLimit(FOOD, "2000");
        budgets.setLimit(FOOD, "2500,50");

        assertEquals(250_050, food().limitMinor());
        assertEquals(1, budgetRepository.findDefaults().size());

        budgets.removeLimit(FOOD);
        assertNull(food().limitMinor());

        budgets.setLimit(FOOD, "3000"); // a removed limit can be set again
        assertEquals(300_000, food().limitMinor());
        budgets.removeLimit(RENT); // nothing to remove: no error
    }

    @Test
    void theDatabaseAllowsOnlyOneLiveLimitPerCategory() throws SQLException {
        budgetRepository.insert(Budget.create(null, 100));
        assertThrows(SQLException.class, () -> budgetRepository.insert(Budget.create(null, 200)));
    }

    @ParameterizedTest(name = "\"{0}\" is rejected")
    @NullSource
    @ValueSource(strings = {"", " ", "0", "0.00", "-5", "abc", "1.234", "1000000000.00"})
    void badLimitsAreRejected(String text) {
        ValidationException error = assertThrows(ValidationException.class, () -> budgets.setLimit(FOOD, text));
        assertEquals(BudgetService.FIELD, error.field());
    }

    @Test
    void limitsAreOnlyForLiveExpenseCategories() {
        assertThrows(ValidationException.class, () -> budgets.setLimit(SALARY, "100"));
        assertThrows(ValidationException.class, () -> budgets.setLimit("no-such-category", "100"));
    }

    @Test
    void spendingInACurrencyWithoutARateIsReportedNotSilentlyDropped() throws SQLException {
        spend("10", SupportedCurrency.USD, "2026-11-05", FOOD);
        spend("100", null, "2026-11-05", FOOD);
        // Switching to ILS with no ILS rate drops all rates (nothing can be expressed in ILS yet).
        currencies.setMainCurrency(SupportedCurrency.ILS);

        MonthBudget month = budgets.forMonth(NOVEMBER);

        assertEquals(Set.of(SupportedCurrency.USD, SupportedCurrency.RUB), month.unconverted());
        assertEquals(0, month.overall().spentMinor());
    }
}
