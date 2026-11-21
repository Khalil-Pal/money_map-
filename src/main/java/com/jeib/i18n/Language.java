package com.jeib.i18n;

import java.util.Locale;

/** Languages supported by the Jeib interface. */
public enum Language {
    ENGLISH(Locale.ENGLISH, false, "English"),
    ARABIC(Locale.forLanguageTag("ar"), true, "العربية"),
    RUSSIAN(Locale.forLanguageTag("ru"), false, "Русский");

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

    public static Language forLocale(Locale locale) {
        for (Language language : values()) {
            if (language.locale.getLanguage().equals(locale.getLanguage())) {
                return language;
            }
        }
        return ENGLISH;
    }

    public boolean rightToLeft() {
        return rightToLeft;
    }

    /** The language's name in itself ("العربية"), for the language picker. */
    public String nativeName() {
        return nativeName;
    }
}
