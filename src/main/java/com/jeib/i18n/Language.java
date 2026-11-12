package com.jeib.i18n;

import java.util.Locale;

/** The languages Jeib speaks (decided Nov 12, 2026: English + Arabic). */
public enum Language {
    ENGLISH(Locale.ENGLISH, false, "English"),
    ARABIC(Locale.forLanguageTag("ar"), true, "العربية");

    private final Locale locale;
    private final boolean rightToLeft;
    private final String nativeName;

    Language(Locale locale, boolean rightToLeft, String nativeName) {
        this.locale = locale;
        this.rightToLeft = rightToLeft;
        this.nativeName = nativeName;
    }

    public Locale locale() {
        return locale;
    }

    public boolean rightToLeft() {
        return rightToLeft;
    }

    /** The language's name in itself ("العربية"), for the language picker. */
    public String nativeName() {
        return nativeName;
    }
}
