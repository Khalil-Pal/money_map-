package com.jeib.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Currency;
import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @ParameterizedTest(name = "\"{0}\" is {1} cents")
    @CsvSource(delimiter = '|', value = {
            "12.50 | 1250",
            "12.5  | 1250",
            "12,5  | 1250",
            "12    | 1200",
            "0.01  | 1",
            "2800  | 280000",
    })
    void parsesTypedAmounts(String text, long cents) {
        assertEquals(cents, Money.parseMinorUnits(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "-5", "1.234", "abc", "12.", ".5", "1 000"})
    void rejectsAnythingElse(String text) {
        assertThrows(IllegalArgumentException.class, () -> Money.parseMinorUnits(text));
    }

    @ParameterizedTest(name = "{0} cents formats as {1}")
    @CsvSource(delimiter = '|', value = {
            "179217 | $1,792.17",
            "5      | $0.05",
            "0      | $0.00",
    })
    void formatsUsDollars(long cents, String expected) {
        assertEquals(expected, Money.format(cents, Currency.getInstance("USD"), Locale.US));
    }
}
