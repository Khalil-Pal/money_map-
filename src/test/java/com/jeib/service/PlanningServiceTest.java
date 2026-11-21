package com.jeib.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.db.Database;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.TransactionType;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.RecurringRepository;
import com.jeib.repository.SavingsGoalRepository;
import com.jeib.repository.SettingsRepository;
import com.jeib.repository.TransactionRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanningServiceTest {

    @TempDir Path folder;

    @Test
    void keepsMonthlyPaymentsAndGoalContributionsSeparateFromTransactions() throws Exception {
        var clock = Clock.fixed(Instant.parse("2027-02-05T12:00:00Z"), ZoneOffset.UTC);
        var database = new Database(folder.resolve("jeib.db"));
        database.migrate();
        var currencies = new CurrencyService(new SettingsRepository(database));
        currencies.setMainCurrency(SupportedCurrency.USD);
        var categories = new CategoryService(new CategoryRepository(database, clock));
        var service = new PlanningService(new RecurringRepository(database, clock),
                new SavingsGoalRepository(database, clock), categories, currencies, clock);
        var expense = categories.list().stream().filter(row -> row.kind() == TransactionType.EXPENSE)
                .findFirst().orElseThrow();

        assertThrows(ValidationException.class, () -> service.createPayment("Rent", "100", "32", expense, SupportedCurrency.ILS));
        service.createPayment("Rent", "100", "٣١", expense, SupportedCurrency.ILS);
        var payment = service.recurring().payments().getFirst();
        assertEquals(LocalDate.of(2027, 2, 28), payment.nextDue(LocalDate.now(clock)));
        assertEquals(LocalDate.of(2027, 3, 31), payment.nextDue(LocalDate.of(2027, 3, 1)));

        service.createGoal("Laptop", "500", SupportedCurrency.RUB);
        var goal = service.goals().goals().getFirst();
        service.addSavings(goal.id(), "12,50");
        service.addSavings(goal.id(), "2.50");
        assertEquals(1_500, service.goals().goals().getFirst().savedMinor());
        assertThrows(ValidationException.class, () -> service.addSavings(goal.id(), "0"));
        assertTrue(new TransactionRepository(database, clock).findByMonth(YearMonth.now(clock)).isEmpty());

        service.archivePayment(payment.id());
        service.archiveGoal(goal.id());
        assertTrue(service.recurring().payments().isEmpty());
        assertTrue(service.goals().goals().isEmpty());
    }
}
