package com.jeib.service;

import com.jeib.model.Category;
import com.jeib.model.Money;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.TransactionType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns one quick-add line into a transaction input. Pure: no database, no UI, so it's easy to test.
 *
 * <pre>
 *   250 food lunch          expense · 250 (main currency) · Food · "lunch"
 *   12.50 usd coffee        expense · $12.50 · Other (no category matched) · "coffee"
 *   $12.50 coffee           the same, with the symbol on the amount
 *   +300 usd salary         income · $300 · Salary
 *   2800 salary             income too: "salary" only matches an income category
 * </pre>
 *
 * Rules, in order:
 * <ol>
 *   <li>The line starts with an amount (at most two decimals; a comma works too). A leading
 *       {@code +} means income.</li>
 *   <li>A currency may be glued to the amount ($12.50, 250₽) or be the next word (usd, ils, nis,
 *       rub, $, ₪, ₽, dollars, shekels, rubles). Without one, the main currency is used.</li>
 *   <li>The next word(s) may name a category, ignoring case: the full name ("other income"), or the
 *       start of exactly one name ("ent" → Entertainment, at least 2 letters). Expense categories
 *       are tried first, unless the line started with {@code +}; a word that only matches an income
 *       category makes it income.</li>
 *   <li>No match: the built-in "Other" / "Other income", and all remaining words are the note.</li>
 *   <li>Everything after the category is the note. The date is today.</li>
 * </ol>
 * Amount limits, rates, and the rest are checked later by {@link TransactionService}, like any input.
 */
public final class QuickAddParser {

    /** The error field name, for {@link ValidationException#field()}. */
    public static final String FIELD = "quickAdd";

    private static final Pattern AMOUNT = Pattern.compile("\\d+([.,]\\d{1,2})?");
    private static final int MIN_PREFIX = 2;

    private static final Map<String, SupportedCurrency> CURRENCY_WORDS = Map.ofEntries(
            Map.entry("usd", SupportedCurrency.USD), Map.entry("$", SupportedCurrency.USD),
            Map.entry("dollar", SupportedCurrency.USD), Map.entry("dollars", SupportedCurrency.USD),
            Map.entry("ils", SupportedCurrency.ILS), Map.entry("nis", SupportedCurrency.ILS),
            Map.entry("₪", SupportedCurrency.ILS), Map.entry("shekel", SupportedCurrency.ILS),
            Map.entry("shekels", SupportedCurrency.ILS),
            Map.entry("rub", SupportedCurrency.RUB), Map.entry("₽", SupportedCurrency.RUB),
            Map.entry("ruble", SupportedCurrency.RUB), Map.entry("rubles", SupportedCurrency.RUB),
            Map.entry("руб", SupportedCurrency.RUB));

    private QuickAddParser() {
    }

    /**
     * The parsed line.
     *
     * @param category        the matched category, or the built-in fallback
     * @param categoryMatched false when no word named a category and the fallback was used
     * @param amountMinor     the amount in minor units, for the preview
     */
    public record Parsed(TransactionInput input, Category category, boolean categoryMatched, long amountMinor) {
    }

    /**
     * @param categories the live categories (including the two built-ins)
     * @param main       the main currency, used when the line names none
     * @throws ValidationException (field {@link #FIELD}) when the line doesn't start with an amount
     */
    public static Parsed parse(String text, List<Category> categories, SupportedCurrency main, LocalDate today) {
        List<String> words = new ArrayList<>(Arrays.asList((text == null ? "" : text).strip().split("\\s+")));
        if (words.getFirst().isEmpty()) {
            throw new ValidationException(FIELD, "Type an amount first, e.g. 250 food lunch.");
        }

        String amount = words.removeFirst();
        boolean income = amount.startsWith("+");
        if (income) {
            amount = amount.substring(1);
            if (amount.isEmpty() && !words.isEmpty()) {
                amount = words.removeFirst(); // "+ 300"
            }
        }
        SupportedCurrency currency = null;
        for (SupportedCurrency candidate : SupportedCurrency.values()) {
            if (amount.startsWith(candidate.symbol())) {
                currency = candidate;
                amount = amount.substring(candidate.symbol().length());
            } else if (amount.endsWith(candidate.symbol())) {
                currency = candidate;
                amount = amount.substring(0, amount.length() - candidate.symbol().length());
            }
        }
        if (!AMOUNT.matcher(amount).matches()) {
            throw new ValidationException(FIELD, "Start with an amount, e.g. 250 food lunch (at most two decimals).");
        }
        long amountMinor;
        try {
            amountMinor = Money.parseMinorUnits(amount);
        } catch (ArithmeticException e) {
            throw new ValidationException(FIELD, "That amount is too large.");
        }

        if (currency == null && !words.isEmpty()) {
            currency = CURRENCY_WORDS.get(words.getFirst().toLowerCase(Locale.ROOT));
            if (currency != null) {
                words.removeFirst();
            }
        }
        if (currency == null) {
            currency = main;
        }

        TransactionType type = income ? TransactionType.INCOME : TransactionType.EXPENSE;
        Match match = find(words, categories, type);
        if (match == null && !income) {
            Match incomeMatch = find(words, categories, TransactionType.INCOME);
            if (incomeMatch != null) {
                match = incomeMatch;
                type = TransactionType.INCOME;
            }
        }
        Category category;
        if (match != null) {
            category = match.category();
            words = words.subList(match.words(), words.size());
        } else {
            category = builtIn(categories, type);
        }
        String note = words.isEmpty() ? null : String.join(" ", words);

        TransactionInput input = new TransactionInput(type, amount, currency, today, category.id(), note);
        return new Parsed(input, category, match != null, amountMinor);
    }

    /** A category named by the first word(s), and how many words its name used. */
    private record Match(Category category, int words) {
    }

    private static Match find(List<String> words, List<Category> categories, TransactionType kind) {
        if (words.isEmpty()) {
            return null;
        }
        List<Category> ofKind = categories.stream().filter(c -> c.kind() == kind).toList();

        // 1. A full name, the longest one first ("other income" beats "other").
        Match best = null;
        for (Category category : ofKind) {
            int length = category.name().split("\\s+").length;
            if (length <= words.size()
                    && String.join(" ", words.subList(0, length)).equalsIgnoreCase(category.name())
                    && (best == null || length > best.words())) {
                best = new Match(category, length);
            }
        }
        if (best != null) {
            return best;
        }

        // 2. The start of exactly one name ("ent" → Entertainment). Ambiguous starts match nothing.
        String first = words.getFirst().toLowerCase(Locale.ROOT);
        if (first.length() < MIN_PREFIX) {
            return null;
        }
        List<Category> starts = ofKind.stream()
                .filter(c -> c.name().toLowerCase(Locale.ROOT).startsWith(first))
                .toList();
        return starts.size() == 1 ? new Match(starts.getFirst(), 1) : null;
    }

    private static Category builtIn(List<Category> categories, TransactionType kind) {
        return categories.stream()
                .filter(c -> c.builtIn() && c.kind() == kind)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The built-in " + kind + " category is missing"));
    }
}
