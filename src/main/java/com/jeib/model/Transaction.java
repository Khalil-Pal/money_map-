package com.jeib.model;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * One income or expense entry.
 *
 * @param id          client-generated UUID, so a retried sync push can never create a duplicate
 * @param accountId   the account it belongs to, or {@code null} while accounts aren't built (Week 8)
 * @param amountMinor amount in minor units (cents), always positive; {@link #type} says which way it goes
 * @param currency    ISO 4217 code, e.g. "USD"
 * @param note        free text, may be {@code null}
 * @param version     starts at 1 and goes up on every change; sync uses it for last-write-wins
 */
public record Transaction(
        String id,
        String accountId,
        String categoryId,
        TransactionType type,
        long amountMinor,
        String currency,
        LocalDate occurredOn,
        String note,
        Instant createdAt,
        Instant updatedAt,
        int version) {

    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(occurredOn, "occurredOn");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amount must be positive, was " + amountMinor);
        }
        if (version < 1) {
            throw new IllegalArgumentException("version starts at 1, was " + version);
        }
    }

    /** A brand-new transaction: fresh UUID, version 1, created and updated now. */
    public static Transaction create(String categoryId, TransactionType type, long amountMinor, String currency,
                                     LocalDate occurredOn, String note, Clock clock) {
        // Millisecond precision, the same as the database stores, so a saved row reads back equal.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        return new Transaction(UUID.randomUUID().toString(), null, categoryId, type, amountMinor, currency,
                occurredOn, note, now, now, 1);
    }
}
