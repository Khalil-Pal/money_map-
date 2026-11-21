package com.jeib.service;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.BudgetPlan;
import com.jeib.model.DateRange;
import com.jeib.model.LeftPerDay;
import com.jeib.model.LimitRules;
import com.jeib.model.LimitStatus;
import com.jeib.model.Money;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.TransactionType;
import com.jeib.repository.BudgetPlanRepository;
import com.jeib.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BudgetPlanService {

    public record Summary(BudgetPlan plan, long spentMinor, List<BudgetService.CategorySpending> byCategory,
                          Set<SupportedCurrency> missingRates, LeftPerDay leftPerDay) {
        public long remainingMinor() { return plan.limitMinor() - spentMinor; }
        public LimitStatus status() { return LimitRules.status(spentMinor, plan.limitMinor()); }
        public int percentUsed() { return LimitRules.percentUsed(spentMinor, plan.limitMinor()); }
    }

    private final BudgetPlanRepository plans;
    private final TransactionRepository transactions;
    private final CategoryService categories;
    private final CurrencyService currencies;

    public BudgetPlanService(BudgetPlanRepository plans, TransactionRepository transactions,
                             CategoryService categories, CurrencyService currencies) {
        this.plans = plans;
        this.transactions = transactions;
        this.categories = categories;
        this.currencies = currencies;
    }

    public List<BudgetPlan> list() throws SQLException {
        return plans.list();
    }

    public BudgetPlan create(String name, LocalDate start, LocalDate end, String amount,
                             SupportedCurrency currency, Set<String> categoryIds) throws SQLException {
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty() || cleanName.codePointCount(0, cleanName.length()) > 60) {
            throw new ValidationException(t("Enter a plan name up to 60 characters."));
        }
        DateRange dates;
        try {
            dates = new DateRange(start, end);
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new ValidationException(t("Choose a start date and an end date on or after it."));
        }
        long limit;
        try {
            limit = Money.parseMinorUnits(amount);
        } catch (IllegalArgumentException | ArithmeticException | NullPointerException error) {
            throw new ValidationException(t("Enter a valid budget amount."));
        }
        if (limit <= 0 || limit > TransactionService.MAX_AMOUNT_MINOR) {
            throw new ValidationException(t("Enter a valid budget amount."));
        }
        if (currency == null) {
            throw new ValidationException(t("Choose a budget currency."));
        }
        var activeExpenses = categories.list().stream().filter(category -> category.kind() == TransactionType.EXPENSE)
                .map(category -> category.id()).toList();
        if (categoryIds.isEmpty() || !activeExpenses.containsAll(categoryIds)) {
            throw new ValidationException(t("Choose at least one available expense category."));
        }
        var plan = new BudgetPlan(UUID.randomUUID().toString(), cleanName, dates, limit, currency, categoryIds);
        plans.insert(plan);
        return plan;
    }

    public Summary summarize(BudgetPlan plan, LocalDate today) throws SQLException {
        var rates = currencies.rates();
        var totals = new LinkedHashMap<String, Map<SupportedCurrency, Long>>();
        for (var transaction : transactions.findByRange(plan.dates(), plan.id())) {
            if (transaction.type() == TransactionType.EXPENSE) {
                totals.computeIfAbsent(transaction.categoryId(), key -> new LinkedHashMap<>())
                        .merge(SupportedCurrency.valueOf(transaction.currency()), transaction.amountMinor(), Math::addExact);
            }
        }
        var missing = EnumSet.noneOf(SupportedCurrency.class);
        var targetRate = rates.rate(plan.currency());
        var converted = new LinkedHashMap<String, Long>();
        for (var entry : totals.entrySet()) {
            long subtotal = 0;
            for (var currencyTotal : entry.getValue().entrySet()) {
                var from = currencyTotal.getKey();
                if (from == plan.currency()) {
                    subtotal = Math.addExact(subtotal, currencyTotal.getValue());
                    continue;
                }
                var sourceRate = rates.rate(from);
                if (sourceRate.isEmpty() || targetRate.isEmpty()) {
                    missing.add(sourceRate.isEmpty() ? from : plan.currency());
                    continue;
                }
                long amount = BigDecimal.valueOf(currencyTotal.getValue()).multiply(sourceRate.get())
                        .divide(targetRate.get(), 0, RoundingMode.HALF_EVEN).longValueExact();
                subtotal = Math.addExact(subtotal, amount);
            }
            converted.put(entry.getKey(), subtotal);
        }
        long spent = converted.values().stream().reduce(0L, Math::addExact);
        LocalDate firstRemaining = today.isBefore(plan.dates().start()) ? plan.dates().start() : today;
        int days = Math.toIntExact(ChronoUnit.DAYS.between(firstRemaining, plan.dates().end()) + 1);
        long remaining = Math.max(0, plan.limitMinor() - spent);
        var breakdown = categories.list().stream()
                .filter(category -> plan.categoryIds().contains(category.id()) || converted.containsKey(category.id()))
                .map(category -> new BudgetService.CategorySpending(category, converted.getOrDefault(category.id(), 0L)))
                .toList();
        return new Summary(plan, spent, breakdown, Set.copyOf(missing),
                days > 0 ? new LeftPerDay(remaining / days, days) : null);
    }
}
