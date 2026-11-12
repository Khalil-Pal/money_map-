package com.jeib.model;

import static com.jeib.model.SupportedCurrency.ILS;
import static com.jeib.model.SupportedCurrency.RUB;
import static com.jeib.model.SupportedCurrency.USD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ExchangeRatesTest {

    /** Main currency RUB: 1 USD = 92.50 RUB, 1 ILS = 25 RUB. */
    private static final ExchangeRates RUB_MAIN =
            new ExchangeRates(RUB, Map.of(USD, new BigDecimal("92.50"), ILS, new BigDecimal("25")));

    @ParameterizedTest
    @EnumSource(SupportedCurrency.class)
    void everySupportedCurrencyHasTwoDecimalsLikeMoneyAssumes(SupportedCurrency currency) {
        assertEquals(2, currency.currency().getDefaultFractionDigits());
    }

    @Test
    void theMainCurrencyConvertsToItself() {
        assertEquals(123_456, RUB_MAIN.toMain(123_456, RUB));
    }

    @Test
    void convertsMinorUnitsIntoTheMainCurrency() {
        assertEquals(115_625, RUB_MAIN.toMain(1_250, USD)); // 12.50 USD = 1156.25 RUB
        assertEquals(112_500, RUB_MAIN.toMain(4_500, ILS)); // 45 ILS = 1125 RUB
    }

    @Test
    void roundsHalfEven() {
        ExchangeRates half = new ExchangeRates(USD, Map.of(RUB, new BigDecimal("0.5")));
        assertEquals(0, half.toMain(1, RUB)); // 0.5 → 0
        assertEquals(2, half.toMain(3, RUB)); // 1.5 → 2
        assertEquals(2, half.toMain(5, RUB)); // 2.5 → 2
    }

    @Test
    void aMissingRateCannotBeConverted() {
        ExchangeRates onlyUsd = new ExchangeRates(RUB, Map.of(USD, new BigDecimal("92.5")));
        assertFalse(onlyUsd.canConvert(ILS));
        assertTrue(onlyUsd.canConvert(RUB));
        assertThrows(IllegalStateException.class, () -> onlyUsd.toMain(100, ILS));
    }

    @Test
    void ratesMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExchangeRates(RUB, Map.of(USD, BigDecimal.ZERO)));
        assertThrows(IllegalArgumentException.class,
                () -> new ExchangeRates(RUB, Map.of(USD, new BigDecimal("-1"))));
    }

    @Test
    void changingTheMainCurrencyReexpressesTheRates() {
        ExchangeRates ilsMain = RUB_MAIN.withMain(ILS);

        assertEquals(ILS, ilsMain.main());
        assertEquals(new BigDecimal("3.700000"), ilsMain.rate(USD).orElseThrow()); // 92.50 / 25
        assertEquals(new BigDecimal("0.040000"), ilsMain.rate(RUB).orElseThrow()); // 1 / 25
        assertEquals(BigDecimal.ONE, ilsMain.rate(ILS).orElseThrow());
    }

    @Test
    void withoutARateForTheNewMainCurrencyAllRatesAreDropped() {
        ExchangeRates onlyUsd = new ExchangeRates(RUB, Map.of(USD, new BigDecimal("92.5")));
        ExchangeRates ilsMain = onlyUsd.withMain(ILS);

        assertTrue(ilsMain.rates().isEmpty());
        assertFalse(ilsMain.canConvert(USD));
    }
}
