package com.jeib.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.util.regex.Pattern;

/** Converts between what people type ("12.50") and stored minor units (1250). Never uses double. */
public final class Money {

    private static final Pattern AMOUNT = Pattern.compile("\\d+([.,]\\d{1,2})?");

    private Money() {
    }

    /**
     * Parses a non-negative amount with at most two decimals; a comma is accepted as the decimal
     * separator. Throws {@link IllegalArgumentException} for anything else ("-5", "1.234", "abc").
     */
    public static long parseMinorUnits(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!AMOUNT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Not an amount: \"" + text + "\"");
        }
        return new BigDecimal(trimmed.replace(',', '.'))
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact();
    }

    public static String format(long minorUnits, Currency currency, Locale locale) {
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(currency);
        return format.format(BigDecimal.valueOf(minorUnits, currency.getDefaultFractionDigits()));
    }
}
