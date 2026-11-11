package com.jeib.service;

import com.jeib.model.Category;
import com.jeib.model.Money;
import com.jeib.model.Transaction;
import com.jeib.model.TransactionType;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.TransactionRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Adds, edits, deletes, and lists transactions. Every write is checked here first, so the UI,
 * quick-add, and (later) CSV import all follow the same rules:
 * <ul>
 *   <li>type is required;</li>
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
    private final String currency;
    private final Clock clock;

    /**
     * @param currency ISO 4217 code for new transactions, e.g. "USD" (per-account currencies come later)
     * @param clock    "now", for timestamps and the future-date rule; fixed in tests
     */
    public TransactionService(TransactionRepository transactions, CategoryRepository categories,
                              String currency, Clock clock) {
        this.transactions = transactions;
        this.categories = categories;
        this.currency = currency;
        this.clock = clock;
    }

    public List<Transaction> listMonth(YearMonth month) throws SQLException {
        return transactions.findByMonth(month);
    }

    public Transaction add(TransactionInput input) throws SQLException {
        Valid valid = validate(input);
        Transaction transaction = Transaction.create(valid.categoryId(), valid.type(), valid.amountMinor(),
                currency, valid.occurredOn(), valid.note(), clock);
        transactions.insert(transaction);
        return transaction;
    }

    /** Replaces what the user can change; id, account, currency, and created_at stay. */
    public Transaction edit(String id, TransactionInput input) throws SQLException {
        Transaction current = transactions.findById(id).orElseThrow(TransactionService::gone);
        Valid valid = validate(input);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Transaction edited = new Transaction(current.id(), current.accountId(), valid.categoryId(), valid.type(),
                valid.amountMinor(), current.currency(), valid.occurredOn(), valid.note(),
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

    // ---- validation ------------------------------------------------------

    /** The checked, cleaned-up input. */
    private record Valid(TransactionType type, long amountMinor, LocalDate occurredOn, String categoryId, String note) {
    }

    private Valid validate(TransactionInput input) throws SQLException {
        Objects.requireNonNull(input, "input");
        if (input.type() == null) {
            throw new ValidationException("type", "Choose expense or income.");
        }
        long amount = parseAmount(input.amountText());
        LocalDate date = checkDate(input.occurredOn());
        Category category = checkCategory(input.categoryId(), input.type());
        return new Valid(input.type(), amount, date, category.id(), cleanNote(input.note()));
    }

    private static long parseAmount(String text) {
        if (text == null || text.isBlank()) {
            throw new ValidationException("amount", "Enter an amount.");
        }
        long amount;
        try {
            amount = Money.parseMinorUnits(text);
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw new ValidationException("amount", "Enter an amount like 12.50 (at most two decimals).");
        }
        if (amount == 0) {
            throw new ValidationException("amount", "The amount must be more than 0.");
        }
        if (amount > MAX_AMOUNT_MINOR) {
            throw new ValidationException("amount", "That amount is too large.");
        }
        return amount;
    }

    private LocalDate checkDate(LocalDate date) {
        if (date == null) {
            throw new ValidationException("date", "Pick a date.");
        }
        LocalDate today = LocalDate.now(clock);
        if (date.isAfter(today.plusYears(1))) {
            throw new ValidationException("date", "The date is more than a year ahead. Check the year.");
        }
        return date;
    }

    private Category checkCategory(String categoryId, TransactionType type) throws SQLException {
        if (categoryId == null) {
            throw new ValidationException("category", "Pick a category.");
        }
        Category category = categories.findActiveById(categoryId)
                .orElseThrow(() -> new ValidationException("category", "That category no longer exists. Pick another."));
        if (category.kind() != type) {
            throw new ValidationException("category", "\"" + category.name() + "\" is an "
                    + category.kind().name().toLowerCase(Locale.ROOT) + " category. Pick an "
                    + type.name().toLowerCase(Locale.ROOT) + " category.");
        }
        return category;
    }

    private static String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String clean = note.strip();
        if (clean.codePointCount(0, clean.length()) > MAX_NOTE_LENGTH) {
            throw new ValidationException("note", "Keep the note to " + MAX_NOTE_LENGTH + " characters or fewer.");
        }
        return clean;
    }

    private static ValidationException gone() {
        return new ValidationException("This transaction was deleted or changed elsewhere. Reload and try again.");
    }
}
