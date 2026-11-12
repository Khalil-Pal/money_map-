package com.jeib.service;

import static com.jeib.model.SupportedCurrency.ILS;
import static com.jeib.model.SupportedCurrency.RUB;
import static com.jeib.model.SupportedCurrency.USD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.db.Database;
import com.jeib.model.ExchangeRates;
import com.jeib.repository.SettingsRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CurrencyServiceTest {

    @TempDir
    Path tempDir;

    private Database database;

    @BeforeEach
    void migrate() {
        database = new Database(tempDir.resolve("test.db"));
        database.migrate();
    }

    /** A fresh service each time, so every check reads what was really saved. */
    private CurrencyService service() {
        return new CurrencyService(new SettingsRepository(database));
    }

    @Test
    void thereIsNoMainCurrencyUntilTheUserChoosesOne() throws SQLException {
        assertEquals(Optional.empty(), service().mainCurrency());
        assertEquals("currency", assertThrows(ValidationException.class, () -> service().rates()).field());

        service().setMainCurrency(RUB);

        assertEquals(Optional.of(RUB), service().mainCurrency());
        assertTrue(service().rates().rates().isEmpty());
    }

    @Test
    void ratesAreSavedAndAcceptACommaAsTheDecimalSeparator() throws SQLException {
        service().setMainCurrency(RUB);
        service().setRate(USD, "92,5");
        service().setRate(ILS, "25.123456");

        ExchangeRates rates = service().rates();
        assertEquals(new BigDecimal("92.5"), rates.rate(USD).orElseThrow());
        assertEquals(new BigDecimal("25.123456"), rates.rate(ILS).orElseThrow());
        assertEquals(115_625, rates.toMain(1_250, USD));
    }

    @Test
    void settingARateAgainReplacesIt() throws SQLException {
        service().setMainCurrency(RUB);
        service().setRate(USD, "92.5");
        service().setRate(USD, "95");

        assertEquals(new BigDecimal("95"), service().rates().rate(USD).orElseThrow());
    }

    @ParameterizedTest(name = "\"{0}\" is rejected")
    @NullSource
    @ValueSource(strings = {"", "abc", "-1", "0", "0.000", "1.1234567", "1000000.01", "12."})
    void badRatesAreRejected(String rate) throws SQLException {
        service().setMainCurrency(RUB);
        assertEquals("rate", assertThrows(ValidationException.class, () -> service().setRate(USD, rate)).field());
    }

    @Test
    void theMainCurrencyHasNoRateToSet() throws SQLException {
        service().setMainCurrency(RUB);
        assertThrows(ValidationException.class, () -> service().setRate(RUB, "1"));
    }

    @Test
    void aRateNeedsAMainCurrencyFirst() {
        assertThrows(ValidationException.class, () -> service().setRate(USD, "92.5"));
    }

    @Test
    void changingTheMainCurrencyKeepsTheRatesCorrect() throws SQLException {
        service().setMainCurrency(RUB);
        service().setRate(USD, "92.5");
        service().setRate(ILS, "25");

        service().setMainCurrency(ILS);

        ExchangeRates rates = service().rates();
        assertEquals(ILS, rates.main());
        assertEquals(0, new BigDecimal("3.7").compareTo(rates.rate(USD).orElseThrow()));
        assertEquals(0, new BigDecimal("0.04").compareTo(rates.rate(RUB).orElseThrow()));
        assertEquals(Optional.empty(), new SettingsRepository(database).get("rate.ILS")); // main is never stored
    }
}
