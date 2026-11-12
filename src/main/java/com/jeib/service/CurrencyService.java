package com.jeib.service;

import com.jeib.model.ExchangeRates;
import com.jeib.model.SupportedCurrency;
import com.jeib.repository.SettingsRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The user's main currency (chosen when they set up their plan) and the manual exchange rates
 * into it. Stored in settings as {@code main_currency = RUB} and {@code rate.USD = 92.5}
 * ("1 USD = 92.5 RUB").
 */
public final class CurrencyService {

    static final String MAIN_KEY = "main_currency";
    static final String RATE_PREFIX = "rate.";

    /** A positive number with at most 6 decimals; a comma also works as the decimal separator. */
    private static final Pattern RATE = Pattern.compile("\\d{1,7}([.,]\\d{1,6})?");
    private static final BigDecimal MAX_RATE = new BigDecimal("1000000");

    private final SettingsRepository settings;

    public CurrencyService(SettingsRepository settings) {
        this.settings = settings;
    }

    /** Empty until the user has chosen one. */
    public Optional<SupportedCurrency> mainCurrency() throws SQLException {
        return settings.get(MAIN_KEY).map(SupportedCurrency::valueOf);
    }

    /** The current rates. Throws a ValidationException if no main currency has been chosen yet. */
    public ExchangeRates rates() throws SQLException {
        SupportedCurrency main = mainCurrency().orElseThrow(
                () -> new ValidationException("currency", "Choose your main currency first."));
        Map<SupportedCurrency, BigDecimal> rates = new EnumMap<>(SupportedCurrency.class);
        settings.findByPrefix(RATE_PREFIX).forEach((key, value) ->
                rates.put(SupportedCurrency.valueOf(key.substring(RATE_PREFIX.length())), new BigDecimal(value)));
        return new ExchangeRates(main, rates);
    }

    /**
     * Sets or changes the main currency. On a change the stored rates are re-expressed in the new
     * currency (see {@link ExchangeRates#withMain}), in the same settings transaction. A change
     * needs a rate for the new currency first. Limits are converted by
     * {@link BudgetService#changeMainCurrency}, which is what the UI calls.
     */
    public void setMainCurrency(SupportedCurrency newMain) throws SQLException {
        Optional<SupportedCurrency> current = mainCurrency();
        if (current.isEmpty()) {
            settings.save(Map.of(MAIN_KEY, newMain.name()));
            return;
        }
        ExchangeRates currentRates = rates();
        if (newMain != current.get() && !currentRates.canConvert(newMain)) {
            throw new ValidationException("currency", "Set how much 1 " + newMain + " is in " + current.get()
                    + " first, so your amounts and limits can be converted.");
        }
        ExchangeRates rebased = currentRates.withMain(newMain);
        Map<String, String> changes = new HashMap<>();
        changes.put(MAIN_KEY, newMain.name());
        for (SupportedCurrency currency : SupportedCurrency.values()) {
            BigDecimal rate = rebased.rates().get(currency);
            changes.put(RATE_PREFIX + currency.name(), rate == null ? null : rate.stripTrailingZeros().toPlainString());
        }
        settings.save(changes);
    }

    /**
     * Saves "1 {@code currency} = {@code rateText} main currency".
     *
     * @param rateText as typed, e.g. "92.50" or "3,7"
     */
    public BigDecimal setRate(SupportedCurrency currency, String rateText) throws SQLException {
        SupportedCurrency main = mainCurrency().orElseThrow(
                () -> new ValidationException("currency", "Choose your main currency first."));
        if (currency == main) {
            throw new ValidationException("rate", main + " is your main currency, so its rate is always 1.");
        }
        BigDecimal rate = parseRate(rateText);
        settings.save(Map.of(RATE_PREFIX + currency.name(), rate.toPlainString()));
        return rate;
    }

    private static BigDecimal parseRate(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!RATE.matcher(trimmed).matches()) {
            throw new ValidationException("rate", "Enter a rate like 92.50 (at most 6 decimals).");
        }
        BigDecimal rate = new BigDecimal(trimmed.replace(',', '.'));
        if (rate.signum() == 0) {
            throw new ValidationException("rate", "The rate must be more than 0.");
        }
        if (rate.compareTo(MAX_RATE) > 0) {
            throw new ValidationException("rate", "That rate is too large. Check it.");
        }
        return rate;
    }
}
