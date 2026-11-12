package com.jeib.service;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.Budget;
import com.jeib.model.Category;
import com.jeib.model.ExchangeRates;
import com.jeib.model.LimitRules;
import com.jeib.model.LimitStatus;
import com.jeib.model.Money;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.TransactionType;
import com.jeib.repository.BudgetRepository;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Monthly limits (CLAUDE.md, "Limit status"): one overall limit and one per expense category, in
 * the main currency, the same for every month. Spent = live EXPENSE transactions of the month,
 * summed per currency and converted once per currency with the user's rates.
 */
public final class BudgetService {

    /** The error field name for the limit amount. */
    public static final String FIELD = "limit";

    private final BudgetRepository budgets;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final CurrencyService currencies;

    public BudgetService(BudgetRepository budgets, TransactionRepository transactions,
                         CategoryRepository categories, CurrencyService currencies) {
        this.budgets = budgets;
        this.transactions = transactions;
        this.categories = categories;
        this.currencies = currencies;
    }

    /**
     * One limit line: the overall one or a category's.
     *
     * @param category   null for the overall line
     * @param spentMinor in main-currency minor units
     * @param limitMinor null when no limit is set
     * @param percent    of the limit used, rounded; 0 without a limit
     */
    public record LimitLine(Category category, long spentMinor, Long limitMinor, LimitStatus status, int percent) {

        public boolean isOverall() {
            return category == null;
        }

        /** What's left before the limit (negative when over); null without a limit. */
        public Long remainingMinor() {
            return limitMinor == null ? null : limitMinor - spentMinor;
        }
    }

    /**
     * The month's limits.
     *
     * @param categories every live expense category, in palette order, with or without a limit
     * @param unconverted currencies that had spending but no rate, so they're missing from the sums
     */
    public record MonthBudget(YearMonth month, SupportedCurrency mainCurrency, LimitLine overall,
                              List<LimitLine> categories, Set<SupportedCurrency> unconverted) {
    }

    public MonthBudget forMonth(YearMonth month) throws SQLException {
        ExchangeRates rates = currencies.rates();
        Map<String, Long> spentByCategory = new HashMap<>();
        Set<SupportedCurrency> unconverted = EnumSet.noneOf(SupportedCurrency.class);
        for (TransactionRepository.Spend spend : transactions.sumExpensesByCategory(month)) {
            SupportedCurrency currency = SupportedCurrency.valueOf(spend.currency());
            if (!rates.canConvert(currency)) {
                unconverted.add(currency);
                continue;
            }
            spentByCategory.merge(spend.categoryId(), rates.toMain(spend.amountMinor(), currency), Long::sum);
        }

        Map<String, Long> limits = new HashMap<>();
        Long overallLimit = null;
        for (Budget budget : budgets.findDefaults()) {
            if (budget.isOverall()) {
                overallLimit = budget.limitMinor();
            } else {
                limits.put(budget.categoryId(), budget.limitMinor());
            }
        }

        List<LimitLine> lines = categories.findActive().stream()
                .filter(c -> c.kind() == TransactionType.EXPENSE)
                .map(c -> line(c, spentByCategory.getOrDefault(c.id(), 0L), limits.get(c.id())))
                .toList();
        // Overall = every expense, including any in categories deleted since (their rows moved to
        // "Other", but be safe and count whatever is there).
        long totalSpent = spentByCategory.values().stream().mapToLong(Long::longValue).sum();
        return new MonthBudget(month, rates.main(), line(null, totalSpent, overallLimit), lines, unconverted);
    }

    private static LimitLine line(Category category, long spent, Long limit) {
        LimitStatus status = LimitRules.status(spent, limit);
        int percent = limit == null ? 0 : LimitRules.percentUsed(spent, limit);
        return new LimitLine(category, spent, limit, status, percent);
    }

    /**
     * Sets or changes a limit.
     *
     * @param categoryId an expense category, or null for the overall limit
     * @param amountText as typed, in the main currency, e.g. "10000" or "10000,50"
     */
    public void setLimit(String categoryId, String amountText) throws SQLException {
        if (categoryId != null) {
            Category category = categories.findActiveById(categoryId)
                    .orElseThrow(() -> new ValidationException(t("This category no longer exists.")));
            if (category.kind() != TransactionType.EXPENSE) {
                throw new ValidationException(t("Limits are for expense categories only."));
            }
        }
        long limit = parseLimit(amountText);
        var existing = budgets.findDefault(categoryId);
        if (existing.isPresent()) {
            budgets.updateLimit(existing.get().id(), limit);
        } else {
            budgets.insert(Budget.create(categoryId, limit));
        }
    }

    /**
     * Switches the main currency and converts every limit with it (CLAUDE.md: limits are in the
     * main currency). 10,000 RUB with 1 ILS = 25 RUB becomes 400 ILS. Needs a rate for the new
     * currency, or nothing changes.
     *
     * <p>The currency switch and the limit updates are separate writes (different repositories), so
     * the currency goes first: if it fails, nothing changed. Converting the limits afterwards is a
     * handful of small updates on a local database.
     */
    public void changeMainCurrency(SupportedCurrency newMain) throws SQLException {
        ExchangeRates rates = currencies.rates();
        SupportedCurrency old = rates.main();
        if (newMain == old) {
            return;
        }
        // Divide by the rate the user typed ("1 USD = 92.5 RUB"), not multiply by its rounded inverse
        // (0.010811): 92,500 RUB must become exactly 1,000.00 USD, not 1,000.02.
        BigDecimal newInOld = rates.rate(newMain).orElseThrow(() -> new ValidationException(
                "currency", t("Set how much 1 {0} is in {1} first, so your limits can be converted.", newMain, old)));
        List<Budget> limits = budgets.findDefaults();
        currencies.setMainCurrency(newMain);
        for (Budget limit : limits) {
            long converted = BigDecimal.valueOf(limit.limitMinor())
                    .divide(newInOld, 0, RoundingMode.HALF_EVEN).longValueExact();
            budgets.updateLimit(limit.id(), Math.max(1, converted));
        }
    }

    /** Removes a limit (soft delete). Removing one that isn't set does nothing. */
    public void removeLimit(String categoryId) throws SQLException {
        var existing = budgets.findDefault(categoryId);
        if (existing.isPresent()) {
            budgets.softDelete(existing.get().id());
        }
    }

    private static long parseLimit(String text) {
        if (text == null || text.isBlank()) {
            throw new ValidationException(FIELD, t("Enter a limit, e.g. 10000."));
        }
        long limit;
        try {
            limit = Money.parseMinorUnits(text);
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw new ValidationException(FIELD, t("Enter a limit like 10000 or 10000.50 (at most two decimals)."));
        }
        if (limit == 0) {
            throw new ValidationException(FIELD, t("The limit must be more than 0."));
        }
        if (limit > TransactionService.MAX_AMOUNT_MINOR) {
            throw new ValidationException(FIELD, t("That limit is too large."));
        }
        return limit;
    }
}
