package com.jeib.model;

import java.time.LocalDate;
import java.util.Set;

public record BudgetPlan(String id, String name, DateRange dates, long limitMinor,
                         SupportedCurrency currency, Set<String> categoryIds) {

    public BudgetPlan {
        categoryIds = Set.copyOf(categoryIds);
    }

    public boolean accepts(TransactionType type, LocalDate date, String categoryId) {
        return type == TransactionType.EXPENSE && date != null && categoryId != null
                && !date.isBefore(dates.start()) && !date.isAfter(dates.end()) && categoryIds.contains(categoryId);
    }
}
