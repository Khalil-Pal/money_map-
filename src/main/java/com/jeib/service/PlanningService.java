package com.jeib.service;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.Category;
import com.jeib.model.Digits;
import com.jeib.model.Money;
import com.jeib.model.RecurringPayment;
import com.jeib.model.SavingsGoal;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.TransactionType;
import com.jeib.repository.RecurringRepository;
import com.jeib.repository.SavingsGoalRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class PlanningService {

    public record RecurringData(SupportedCurrency currency, List<Category> categories, List<RecurringPayment> payments,
                                LocalDate today) { }

    public record GoalsData(SupportedCurrency currency, List<SavingsGoal> goals) { }

    private final RecurringRepository recurring;
    private final SavingsGoalRepository goals;
    private final CategoryService categories;
    private final CurrencyService currencies;
    private final Clock clock;

    public PlanningService(RecurringRepository recurring, SavingsGoalRepository goals,
                           CategoryService categories, CurrencyService currencies, Clock clock) {
        this.recurring = recurring;
        this.goals = goals;
        this.categories = categories;
        this.currencies = currencies;
        this.clock = clock;
    }

    public RecurringData recurring() throws SQLException {
        var currency = currencies.rates().main();
        LocalDate today = LocalDate.now(clock);
        var rows = recurring.list(currency).stream().sorted(Comparator.comparing(payment -> payment.nextDue(today))).toList();
        return new RecurringData(currency, expenseCategories(), rows, today);
    }

    public GoalsData goals() throws SQLException {
        return new GoalsData(currencies.rates().main(), goals.list());
    }

    public void createPayment(String name, String amount, String day, Category category, SupportedCurrency currency)
            throws SQLException {
        int paymentDay;
        try {
            paymentDay = Integer.parseInt(day == null ? "" : Digits.normalize(day.strip()));
        } catch (NumberFormatException error) {
            throw new ValidationException(t("Enter a day from 1 to 31."));
        }
        if (paymentDay < 1 || paymentDay > 31) {
            throw new ValidationException(t("Enter a day from 1 to 31."));
        }
        if (category == null || expenseCategories().stream().noneMatch(row -> row.id().equals(category.id()))) {
            throw new ValidationException(t("Pick a category."));
        }
        recurring.insert(new RecurringPayment(UUID.randomUUID().toString(), name(name), category.id(),
                positiveAmount(amount), requireCurrency(currency), paymentDay));
    }

    public void createGoal(String name, String amount, SupportedCurrency currency) throws SQLException {
        goals.insert(new SavingsGoal(UUID.randomUUID().toString(), name(name), positiveAmount(amount), 0,
                requireCurrency(currency)));
    }

    public void addSavings(String goalId, String amount) throws SQLException {
        if (!goals.addSavings(goalId, positiveAmount(amount), TransactionService.MAX_AMOUNT_MINOR)) {
            throw new ValidationException(t("This goal is unavailable or the saved total is too large."));
        }
    }

    public void archivePayment(String id) throws SQLException {
        recurring.archive(id);
    }

    public void archiveGoal(String id) throws SQLException {
        goals.archive(id);
    }

    private List<Category> expenseCategories() throws SQLException {
        return categories.list().stream().filter(category -> category.kind() == TransactionType.EXPENSE).toList();
    }

    private static String name(String input) {
        String name = input == null ? "" : input.strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 60) {
            throw new ValidationException(t("Enter a name up to 60 characters."));
        }
        return name;
    }

    private static long positiveAmount(String text) {
        long amount;
        try {
            amount = Money.parseMinorUnits(text);
        } catch (IllegalArgumentException | ArithmeticException error) {
            throw new ValidationException(t("Enter a valid amount greater than zero."));
        }
        if (amount <= 0 || amount > TransactionService.MAX_AMOUNT_MINOR) {
            throw new ValidationException(t("Enter a valid amount greater than zero."));
        }
        return amount;
    }

    private static SupportedCurrency requireCurrency(SupportedCurrency currency) {
        if (currency == null) {
            throw new ValidationException(t("Choose a currency."));
        }
        return currency;
    }
}
