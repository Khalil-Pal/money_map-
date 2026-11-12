package com.jeib.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A spending or income category.
 *
 * @param colorIndex 0-based index into the 14-color chart palette (jeib-color-system.md §5)
 * @param builtIn    true for "Other" and "Other income", which can't be deleted
 */
public record Category(String id, String name, TransactionType kind, int colorIndex, boolean builtIn) {

    /** Number of colors in the chart palette; color indexes run from 0 to PALETTE_SIZE - 1. */
    public static final int PALETTE_SIZE = 14;

    public Category {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        if (colorIndex < 0 || colorIndex >= PALETTE_SIZE) {
            throw new IllegalArgumentException("color index must be 0-" + (PALETTE_SIZE - 1) + ", was " + colorIndex);
        }
    }

    /** A new user category with a fresh client-generated UUID. Only migrations create built-ins. */
    public static Category create(String name, TransactionType kind, int colorIndex) {
        return new Category(UUID.randomUUID().toString(), name, kind, colorIndex, false);
    }
}
