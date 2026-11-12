package com.jeib.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LimitRulesTest {

    private static final long LIMIT = 45_000; // $450.00

    @ParameterizedTest(name = "{0} of 450.00 is {1}")
    @CsvSource({
            "0,     OK",
            "35999, OK",        // 79.998%
            "36000, WARNING",   // exactly 80%
            "41260, WARNING",   // 91.7%
            "44999, WARNING",   // 99.998%
            "45000, EXCEEDED",  // exactly 100%
            "45001, EXCEEDED",
            "90000, EXCEEDED",
    })
    void statusAtTheEdges(long spent, LimitStatus expected) {
        assertEquals(expected, LimitRules.status(spent, LIMIT));
    }

    @Test
    void noLimitSet() {
        assertEquals(LimitStatus.NO_LIMIT, LimitRules.status(90_000, null));
        assertEquals(LimitStatus.NO_LIMIT, LimitRules.status(90_000, 0L));
    }

    @Test
    void percentUsedRoundsDownSoItNeverContradictsTheStatus() {
        assertEquals(91, LimitRules.percentUsed(41_260, LIMIT)); // 91.69%
        assertEquals(80, LimitRules.percentUsed(36_000, LIMIT)); // exactly 80%
        assertEquals(79, LimitRules.percentUsed(35_999, LIMIT)); // 79.998%: OK, so not "80%"
        assertEquals(99, LimitRules.percentUsed(44_999, LIMIT)); // 99.998%: WARNING, so not "100%"
        assertEquals(138, LimitRules.percentUsed(13_800, 10_000));
        assertEquals(0, LimitRules.percentUsed(1, 200));         // 0.5%
    }

    @Test
    void percentNeedsAPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> LimitRules.percentUsed(100, 0));
    }
}
