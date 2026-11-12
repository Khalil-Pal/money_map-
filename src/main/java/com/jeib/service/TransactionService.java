package com.jeib.service;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.Category;
import com.jeib.model.ExchangeRates;
import com.jeib.model.Money;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.Transaction;
import com.jeib.model.TransactionType;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.TransactionRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Adds, edits, deletes, and lists transactions. Every write is checked here first, so the UI,
 * quick-add, and (later) CSV import all follow the same rules:
 * <ul>
 *   <li>type is required;</li>
 *   <li>the currency is USD, ILS or RUB (default: the main currency), and if it isn't the main
 *       currency, its exchange rate has been set, so totals can be converted;</li>
 *   <li>the amount parses (at most two decimals), is more than 0, and at most 999,999,999.99;</li>
 *   <li>the date is required and at most one year ahead (catches typos like 2062);</li>
 *   <li>the category exists, isn't deleted, and has the same kind as the type;</li>
 *   <li>the note is optional, trimmed, at most 200 characters.</li>
 * </ul>
 */
public final class TransactionService {

    /** Largest single amount, in minor units. Keeps sums far away from long overflow. */
    public static final long MAX_AMOUNT_MINOR = 99_999_999_999L;
    public static final int MAX_NOTE_LENGTH = 200;

    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final CurrencyService currencies;
    private final Clock clock;

    /**
     * @param currencies the main currency and exchange rates
     * @param clock    "now", for timestamps and the future-date rule; fixed in tests
     */
    public TransactionService(TransactionRepository transactions, CategoryRepository categories,
                              CurrencyService currencies, Clock clock) {
        this.transactions = transactions;
        this.categories = categories;
        this.currencies = currencies;
        this.clock = clock;
    }

    public List<Transaction> listMonth(YearMonth month) throws SQLException {
        return transactions.findByMonth(month);
    }

    public Transaction add(TransactionInput input) throws SQLException {
        Valid valid = validate(input);
        Transaction transaction = Transaction.create(valid.categoryId(), valid.type(), valid.amountMinor(),
                valid.currency().name(), valid.occurredOn(), valid.note(), clock);
        transactions.insert(transaction);
        return transaction;
    }

    /** Replaces what the user can change; id, account, and created_at stay. */
    public Transaction edit(String id, TransactionInput input) throws SQLException {
        Transaction current = transactions.findById(id).orElseThrow(TransactionService::gone);
        Valid valid = validate(input);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Transaction edited = new Transaction(current.id(), current.accountId(), valid.categoryId(), valid.type(),
                valid.amountMinor(), valid.currency().name(), valid.occurredOn(), valid.note(),
                current.createdAt(), now, current.version() + 1);
        if (!transactions.update(edited)) {
            // Deleted, or changed since we read it: never overwrite silently.
            throw gone();
        }
        return edited;
    }

    public void delete(String id) throws SQLException {
        if (!transactions.softDelete(id)) {
            throw gone();
        }
    }

    /** Undoes a delete: the soft-deleted row comes back (with a new version, for sync). */
    public void restore(String id) throws SQLException {
        if (!transactions.restore(id)) {
            throw new ValidationException(t("This transaction can't be brought back: it isn't deleted any more."));
        }
    }

    /** The transaction as stored, deleted or not; for undo, which needs the values before a change. */
    public Optional<Transaction> find(String id) throws SQLException {
        return transactions.findById(id);
    }

    /** The input that would recreate {@code t}'s editable values (used to undo or redo an edit). */
    public static TransactionInput inputOf(Transaction t) {
        return new TransactionInput(t.type(), BigDecimal.valueOf(t.amountMinor(), 2).toPlainString(),
                SupportedCurrency.valueOf(t.currency()), t.occurredOn(), t.categoryId(), t.note());
    }

    // ---- validation ------------------------------------------------------

    /** The checked, cleaned-up input. */
    private record Valid(TransactionType type, long amountMinor, SupportedCurrency currency, LocalDate occurredOn,
                         String categoryId, String note) {
    }

    private Valid validate(TransactionInput input) throws SQLException {
        Objects.requireNonNull(input, "input");
        if (input.type() == null) {
            throw new ValidationException("type", t("Choose expense or income."));
        }
        long amount = parseAmount(input.amountText());
        SupportedCurrency currency = checkCurrency(input.currency());
        LocalDate date = checkDate(input.occurredOn());
        Category category = checkCategory(input.categoryId(), input.type());
        return new Valid(input.type(), amount, currency, date, category.id(), cleanNote(input.note()));
    }

    private SupportedCurrency checkCurrency(SupportedCurrency requested) throws SQLException {
        ExchangeRates rates = currencies.rates(); // fails if no main currency is chosen yet
        SupportedCurrency currency = requested == null ? rates.main() : requested;
        if (!rates.canConvert(currency)) {
            throw new ValidationException("currency", t(
                    "Set how much 1 {0} is in {1} first, so this amount can be counted in your totals.",
                    currency, rates.main()));
        }
        return currency;
    }

    private static long parseAmount(String text) {
        if (text == null || text.isBlank()) {
            throw new ValidationException("amount", t("Enter an amount."));
        }
        long amount;
        try {
            amount = Money.parseMinorUnits(text);
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw new ValidationException("amount", t("Enter an amount like 12.50 (at most two decimals)."));
        }
        if (amount == 0) {
            throw new ValidationException("amount", t("The amount must be more than 0."));
        }
        if (amount > MAX_AMOUNT_MINOR) {
            throw new ValidationException("amount", t("That amount is too large."));
        }
        return amount;
    }

    private LocalDate checkDate(LocalDate date) {
        if (date == null) {
            throw new ValidationException("date", t("Pick a date."));
        }
        LocalDate today = LocalDate.now(clock);
        if (date.isAfter(today.plusYears(1))) {
            throw new ValidationException("date", t("The date is more than a year ahead. Check the year."));
        }
        return date;
    }

    private Category checkCategory(String categoryId, TransactionType type) throws SQLException {
        if (categoryId == null) {
            throw new ValidationException("category", t("Pick a category."));
        }
        Category category = categories.findActiveById(categoryId)
                .orElseThrow(() -> new ValidationException("category", t("That category no longer exists. Pick another.")));
        if (category.kind() != type) {
            throw new ValidationException("category", category.kind() == TransactionType.INCOME
                    ? t("\"{0}\" is an income category. Pick an expense category.", category.name())
                    : t("\"{0}\" is an expense category. Pick an income category.", category.name()));
        }
        return category;
    }

    private static String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String clean = note.strip();
        if (clean.codePointCount(0, clean.length()) > MAX_NOTE_LENGTH) {
            throw new ValidationException("note", t("Keep the note to {0} characters or fewer.", MAX_NOTE_LENGTH));
        }
        return clean;
    }

    private static ValidationException gone() {
        return new ValidationException(t("This transaction was deleted or changed elsewhere. Reload and try again."));
    }
}
