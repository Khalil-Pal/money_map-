package com.jeib.model;

import java.util.Currency;

/**
 * The currencies Jeib supports (decided Nov 11, 2026). All three have two decimals (cents, agorot,
 * kopecks), which is what {@link Money#parseMinorUnits} assumes; a currency with three decimals
 * (e.g. the Jordanian dinar) would need Money changes first.
 */
public enum SupportedCurrency {
    USD,
    ILS,
    RUB;

    public Currency currency() {
        return Currency.getInstance(name());
    }
}
