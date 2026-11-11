package com.jeib.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SupportedCurrencyTest {

    @ParameterizedTest(name = "{1} minor units of {0} → {2}")
    @CsvSource(delimiter = '|', value = {
            "USD | 125000 | $1,250.00",
            "USD | 5      | $0.05",
            "USD | 0      | $0.00",
            "USD | -1250  | -$12.50",
            "ILS | 4500   | ₪45.00",
            "RUB | 95000  | 950.00 ₽",
            "RUB | 12345678 | 123,456.78 ₽",
    })
    void formatsTheSameWayInEveryLocale(SupportedCurrency currency, long minorUnits, String expected) {
        assertEquals(expected, currency.format(minorUnits));
    }
}
