package com.jeib.model;

public record SavingsGoal(String id, String name, long targetMinor, long savedMinor, SupportedCurrency currency) {
}
