package com.jeib.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class ThemeModeTest {

    @ParameterizedTest(name = "Automatic at {0} is {1}")
    @CsvSource({
            "00:00, DARK",
            "06:59, DARK",
            "07:00, LIGHT",   // light from 7 AM
            "12:00, LIGHT",
            "18:59, LIGHT",
            "19:00, DARK",    // dark from 7 PM
            "23:59, DARK",
    })
    void automaticFollowsTheClock(LocalTime time, Theme expected) {
        assertEquals(expected, ThemeMode.AUTO.resolve(time));
    }

    @Test
    void lightAndDarkIgnoreTheClock() {
        assertEquals(Theme.LIGHT, ThemeMode.LIGHT.resolve(LocalTime.of(23, 0)));
        assertEquals(Theme.DARK, ThemeMode.DARK.resolve(LocalTime.NOON));
    }

    @Test
    void theButtonCyclesLightDarkAutomatic() {
        assertEquals(ThemeMode.DARK, ThemeMode.LIGHT.next());
        assertEquals(ThemeMode.AUTO, ThemeMode.DARK.next());
        assertEquals(ThemeMode.LIGHT, ThemeMode.AUTO.next());
    }

    @Test
    void savedValuesAreReadBackAndOldOnesStillWork() {
        assertEquals(ThemeMode.AUTO, ThemeMode.parse("AUTO"));
        assertEquals(ThemeMode.DARK, ThemeMode.parse("DARK")); // saved before Automatic existed
        assertEquals(ThemeMode.LIGHT, ThemeMode.parse(null));
        assertEquals(ThemeMode.LIGHT, ThemeMode.parse("purple"));
    }
}
