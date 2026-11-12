package com.jeib.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A monthly spending limit, in minor units of the user's main currency.
 *
 * @param categoryId the expense category it limits, or {@code null} for the overall limit
 * @param limitMinor always positive
 */
public record Budget(String id, String categoryId, long limitMinor) {

    public Budget {
        Objects.requireNonNull(id, "id");
        if (limitMinor <= 0) {
            throw new IllegalArgumentException("limit must be positive, was " + limitMinor);
        }
    }

    /** A new limit with a fresh client-generated UUID. */
    public static Budget create(String categoryId, long limitMinor) {
        return new Budget(UUID.randomUUID().toString(), categoryId, limitMinor);
    }

    public boolean isOverall() {
        return categoryId == null;
    }
}
