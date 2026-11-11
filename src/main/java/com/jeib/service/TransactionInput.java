package com.jeib.service;

import com.jeib.model.SupportedCurrency;
import com.jeib.model.TransactionType;
import java.time.LocalDate;

/**
 * What the user entered in the add/edit dialog (or quick-add), before it's checked. Fields may be
 * null or invalid; {@link TransactionService} decides.
 *
 * @param amountText the amount as typed, e.g. "12.50" or "12,5"
 * @param currency   USD, ILS or RUB; null means the user's main currency
 */
public record TransactionInput(
        TransactionType type,
        String amountText,
        SupportedCurrency currency,
        LocalDate occurredOn,
        String categoryId,
        String note) {
}
