package com.jeib.model;

import static com.jeib.i18n.Messages.t;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Currency;
import java.util.Locale;

/**
 * The currencies Jeib supports (decided Nov 11, 2026). All three have two decimals (cents, agorot,
 * kopecks), which is what {@link Money#parseMinorUnits} assumes; a currency with three decimals
 * (e.g. the Jordanian dinar) would need Money changes first.
 */
public enum SupportedCurrency {
    USD("$", true, "US dollar"),
    ILS("₪", true, "Israeli shekel"),
    RUB("₽", false, "Russian ruble");

    private final String symbol;
    private final boolean symbolFirst;
    private final String displayName;

    SupportedCurrency(String symbol, boolean symbolFirst, String displayName) {
        this.symbol = symbol;
        this.symbolFirst = symbolFirst;
        this.displayName = displayName;
    }

    public Currency currency() {
        return Currency.getInstance(name());
    }

    public String symbol() {
        return symbol;
    }

    /** "Russian ruble", in the app's language. */
    public String displayName() {
        return t(displayName);
    }

    /**
     * Display text for an amount: "$1,250.00", "₪45.00", "950.00 ₽". Same digit style for every
     * currency (comma groups, dot decimals) so a mixed list lines up and reads the same way.
     */
    public String format(long minorUnits) {
        return com.jeib.i18n.Messages.ltr(formatPlain(minorUnits));
    }

    /** Like {@link #format} but without the right-to-left protection, for building signed text ("+$3"). */
    public String formatPlain(long minorUnits) {
        DecimalFormat digits = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        String number = digits.format(BigDecimal.valueOf(Math.abs(minorUnits), 2));
        String sign = minorUnits < 0 ? "-" : "";
        return symbolFirst ? sign + symbol + number : sign + number + " " + symbol;
    }
}
