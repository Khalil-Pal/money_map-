package com.jeib.model;

/**
 * A spending or income category.
 *
 * @param colorIndex 0-based index into the 14-color chart palette (jeib-color-system.md §5)
 * @param builtIn    true for "Other" and "Other income", which can't be deleted
 */
public record Category(String id, String name, TransactionType kind, int colorIndex, boolean builtIn) {
}
