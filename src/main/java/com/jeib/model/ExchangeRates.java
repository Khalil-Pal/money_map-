package com.jeib.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Manual exchange rates into the user's main currency: {@code rates.get(USD) = 92.50} means
 * "1 USD = 92.50 of the main currency". The main currency itself is always 1 and isn't stored.
 *
 * <p>BigDecimal only, never double. Conversions round half-even (banker's rounding), so rounding
 * errors don't all lean the same way when many amounts are converted.
 */
public record ExchangeRates(SupportedCurrency main, Map<SupportedCurrency, BigDecimal> rates) {

    /** Rates are kept to 6 decimals, e.g. 0.040000 RUB → ILS. */
    public static final int RATE_SCALE = 6;

    public ExchangeRates {
        Objects.requireNonNull(main, "main");
        Objects.requireNonNull(rates, "rates");
        EnumMap<SupportedCurrency, BigDecimal> copy = new EnumMap<>(SupportedCurrency.class);
        rates.forEach((currency, rate) -> {
            if (currency == main) {
                return; // always 1, never stored
            }
            if (rate == null || rate.signum() <= 0) {
                throw new IllegalArgumentException("rate for " + currency + " must be positive, was " + rate);
            }
            copy.put(currency, rate);
        });
        rates = Map.copyOf(copy);
    }

    /** How much 1 unit of {@code currency} is in the main currency, if known. */
    public Optional<BigDecimal> rate(SupportedCurrency currency) {
        return currency == main ? Optional.of(BigDecimal.ONE) : Optional.ofNullable(rates.get(currency));
    }

    public boolean canConvert(SupportedCurrency currency) {
        return rate(currency).isPresent();
    }

    /**
     * Converts minor units of {@code from} to minor units of the main currency. Works directly on
     * minor units because every supported currency has two decimals.
     *
     * @throws IllegalStateException if there's no rate for {@code from}
     */
    public long toMain(long minorUnits, SupportedCurrency from) {
        BigDecimal rate = rate(from).orElseThrow(() -> new IllegalStateException("No rate for " + from));
        return BigDecimal.valueOf(minorUnits).multiply(rate).setScale(0, RoundingMode.HALF_EVEN).longValueExact();
    }

    /**
     * The same rates expressed in a new main currency. If 1 USD = 92.50 RUB and 1 ILS = 25 RUB, then
     * with ILS as main: 1 USD = 92.50 / 25 = 3.70 ILS and 1 RUB = 1 / 25 = 0.04 ILS.
     * If there's no rate for the new main currency, nothing can be expressed in it, so the result has
     * no rates and the user has to enter them again.
     */
    public ExchangeRates withMain(SupportedCurrency newMain) {
        if (newMain == main) {
            return this;
        }
        Optional<BigDecimal> newMainRate = rate(newMain);
        if (newMainRate.isEmpty()) {
            return new ExchangeRates(newMain, Map.of());
        }
        BigDecimal divisor = newMainRate.get();
        Map<SupportedCurrency, BigDecimal> rebased = new EnumMap<>(SupportedCurrency.class);
        rebased.put(main, BigDecimal.ONE.divide(divisor, RATE_SCALE, RoundingMode.HALF_EVEN));
        rates.forEach((currency, rate) -> {
            if (currency != newMain) {
                rebased.put(currency, rate.divide(divisor, RATE_SCALE, RoundingMode.HALF_EVEN));
            }
        });
        return new ExchangeRates(newMain, rebased);
    }
}
