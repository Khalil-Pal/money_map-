package com.jeib.service;

import com.jeib.model.TransactionType;
import java.time.LocalDate;

/**
 * What the user entered in the add/edit dialog (or quick-add), before it's checked. Fields may be
 * null or invalid; {@link TransactionService} decides.
 *
 * @param amountText the amount as typed, e.g. "12.50" or "12,5"
 */
public record TransactionInput(
        TransactionType type,
        String amountText,
        LocalDate occurredOn,
        String categoryId,
        String note) {
}
