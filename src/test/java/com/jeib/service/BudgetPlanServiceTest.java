package com.jeib.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jeib.db.Database;
import com.jeib.model.DateRange;
import com.jeib.model.LimitStatus;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.TransactionType;
import com.jeib.repository.BudgetPlanRepository;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.SettingsRepository;
import com.jeib.repository.TransactionRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BudgetPlanServiceTest {

    private static final LocalDate FIRST = LocalDate.of(2026, 11, 21);
    private static final LocalDate LAST = LocalDate.of(2026, 12, 4);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC);

    @TempDir Path folder;
    private BudgetPlanService plans;
    private TransactionService transactions;
    private TransactionCommands commands;
    private String food;
    private String rent;

    @BeforeEach
    void setUp() throws Exception {
        var database = new Database(folder.resolve("jeib.db"));
        database.migrate();
        var categories = new CategoryRepository(database, CLOCK);
        food = categories.findActive().stream().filter(category -> category.name().equals("Food")).findFirst().orElseThrow().id();
        rent = categories.findActive().stream().filter(category -> category.name().equals("Rent")).findFirst().orElseThrow().id();
        var currencies = new CurrencyService(new SettingsRepository(database));
        currencies.setMainCurrency(SupportedCurrency.USD);
        currencies.setRate(SupportedCurrency.ILS, "0.25");
        var transactionRepository = new TransactionRepository(database, CLOCK);
        var planRepository = new BudgetPlanRepository(database, CLOCK);
        plans = new BudgetPlanService(planRepository, transactionRepository, new CategoryService(categories), currencies);
        transactions = new TransactionService(transactionRepository, categories, currencies, CLOCK, planRepository);
        commands = new TransactionCommands(transactions, new UndoManager());
    }

    @Test
    void aPlanNeedsANameAndUsesItsOwnCurrency() throws Exception {
        assertThrows(ValidationException.class,
                () -> plans.create("   ", FIRST, LAST, "100", SupportedCurrency.ILS, Set.of(food)));
        assertThrows(ValidationException.class,
                () -> plans.create("Groceries", FIRST, LAST, "100", null, Set.of(food)));

        var plan = plans.create("  Groceries  ", FIRST, LAST, "100", SupportedCurrency.ILS, Set.of(food));
        commands.add(expense(plan.id(), FIRST, food, "12.50", SupportedCurrency.USD));

        assertEquals("Groceries", plan.name());
        assertEquals(SupportedCurrency.ILS, plans.list().getFirst().currency());
        assertEquals(5_000, plans.summarize(plan, FIRST).spentMinor());
        assertEquals(5_000, plans.summarize(plan, FIRST).remainingMinor());
    }

    @Test
    void overlappingPlansCountOnlyAssignedSpendingAndHonorBothDates() throws Exception {
        var groceries = plans.create("Groceries", FIRST, LAST, "100", SupportedCurrency.USD, Set.of(food));
        var extra = plans.create("Extra", FIRST, LAST, "100", SupportedCurrency.USD, Set.of(food));
        commands.add(expense(groceries.id(), FIRST, food, "50", SupportedCurrency.USD));
        commands.add(expense(groceries.id(), LAST, food, "120", SupportedCurrency.ILS));
        commands.add(expense(extra.id(), FIRST, food, "20", SupportedCurrency.USD));
        commands.add(expense(null, FIRST, food, "17", SupportedCurrency.USD));
        var summary = plans.summarize(groceries, FIRST);
        assertEquals(8_000, summary.spentMinor());
        assertEquals(2_000, summary.remainingMinor());
        assertEquals(LimitStatus.WARNING, summary.status());
        assertEquals(14, summary.leftPerDay().daysLeft());
        assertEquals(142, summary.leftPerDay().perDayMinor());
        assertNull(plans.summarize(groceries, LAST.plusDays(1)).leftPerDay());
        assertEquals(2_000, plans.summarize(extra, FIRST).spentMinor());
        assertEquals(4, transactions.listRange(new DateRange(FIRST, LAST)).size());
    }

    @Test
    void invalidAssignmentIsRejectedAndUndoRestoresRealAssignments() throws Exception {
        var plan = plans.create("Groceries", FIRST, LAST, "20", SupportedCurrency.USD, Set.of(food));
        assertThrows(ValidationException.class,
                () -> commands.add(expense(plan.id(), FIRST.minusDays(1), food, "5", SupportedCurrency.USD)));
        assertThrows(ValidationException.class,
                () -> commands.add(expense(plan.id(), FIRST, rent, "5", SupportedCurrency.USD)));
        var transaction = commands.add(expense(plan.id(), FIRST, food, "20", SupportedCurrency.USD));
        assertEquals(LimitStatus.EXCEEDED, plans.summarize(plan, FIRST).status());
        commands.edit(transaction.id(), expense(null, FIRST, food, "20", SupportedCurrency.USD));
        assertEquals(0, plans.summarize(plan, FIRST).spentMinor());
        commands.history().undo();
        assertEquals(plan.id(), transactions.find(transaction.id()).orElseThrow().budgetPlanId());
        commands.delete(transaction.id());
        assertEquals(0, plans.summarize(plan, FIRST).spentMinor());
        commands.history().undo();
        assertEquals(2_000, plans.summarize(plan, FIRST).spentMinor());
    }

    private TransactionInput expense(String plan, LocalDate date, String category, String amount,
                                     SupportedCurrency currency) {
        return new TransactionInput(TransactionType.EXPENSE, amount, currency, date, category, null, plan);
    }
}
