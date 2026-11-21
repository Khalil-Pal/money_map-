package com.jeib.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jeib.db.Database;
import com.jeib.model.DateRange;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.SettingsRepository;
import com.jeib.service.CurrencyService;
import com.jeib.service.PreferencesService;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** The month switcher. Plain JavaFX properties work without starting the UI toolkit. */
class MainViewModelTest {

    private static MainViewModel openedOn(String instant, String zone) {
        Database unused = new Database(Path.of("never-opened.db"));
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneId.of(zone));
        return new MainViewModel(unused, new CategoryRepository(unused, clock),
                new CurrencyService(new SettingsRepository(unused)),
                new PreferencesService(new SettingsRepository(unused)), clock);
    }

    @Test
    void opensOnTheCurrentMonth() {
        MainViewModel viewModel = openedOn("2026-11-12T10:00:00Z", "UTC");

        assertEquals(YearMonth.of(2026, 11), viewModel.monthProperty().get());
        assertEquals("November 2026", viewModel.monthLabel().get());
    }

    @Test
    void theLocalTimeZoneDecidesTheMonth() {
        // 23:30 UTC on Nov 30 is already Dec 1 in Moscow (UTC+3).
        assertEquals(YearMonth.of(2026, 12), openedOn("2026-11-30T23:30:00Z", "Europe/Moscow").monthProperty().get());
    }

    @Test
    void previousAndNextCrossYearBoundaries() {
        MainViewModel viewModel = openedOn("2026-12-15T10:00:00Z", "UTC");

        viewModel.nextMonth();
        assertEquals("January 2027", viewModel.monthLabel().get());

        viewModel.previousMonth();
        viewModel.previousMonth();
        assertEquals(YearMonth.of(2026, 11), viewModel.monthProperty().get());
    }

    @Test
    void clearingACustomRangeReturnsToTheSelectedMonth() {
        MainViewModel viewModel = openedOn("2026-11-12T10:00:00Z", "UTC");
        LocalDate start = LocalDate.parse("2026-10-25");
        LocalDate end = LocalDate.parse("2026-11-07");
        DateRange range = new DateRange(start, end);

        assertThrows(IllegalArgumentException.class, () -> new DateRange(end, start));
        viewModel.setCustomRange(range);
        assertEquals(range, viewModel.selectedDates());
        viewModel.nextMonth(); // The calendar month can still be changed on Budgets.
        assertEquals(range, viewModel.selectedDates());
        viewModel.setCustomRange(null);
        assertEquals(DateRange.ofMonth(YearMonth.of(2026, 12)), viewModel.selectedDates());
    }
}
