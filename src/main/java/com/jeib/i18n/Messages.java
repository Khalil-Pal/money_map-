package com.jeib.i18n;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Translated text, gettext-style: the English sentence is the key, so code stays readable
 * ({@code t("Enter an amount.")}) and English is always the fallback. Other languages map English
 * to their text ({@link Arabic}); a test checks every sentence used in code and FXML has a
 * translation.
 *
 * <p>Placeholders are {@code {0}}, {@code {1}}… and are replaced literally (no MessageFormat, so
 * apostrophes in "can't" need no escaping and numbers are never re-formatted).
 *
 * <p>The language is app-wide: Jeib is a single-user desktop app, and the whole window is rebuilt
 * when the language changes, so one static setting is the simplest correct choice.
 */
public final class Messages {

    private static final Map<Language, Map<String, String>> TRANSLATIONS = Map.of(Language.ARABIC, Arabic.TEXTS);

    private static volatile Language language = Language.ENGLISH;

    private Messages() {
    }

    public static Language language() {
        return language;
    }

    public static void setLanguage(Language chosen) {
        language = chosen;
    }

    public static Locale locale() {
        return language.locale();
    }

    public static boolean rightToLeft() {
        return language.rightToLeft();
    }

    /** {@code english} in the current language, with {0}, {1}… replaced by {@code args}. */
    public static String t(String english, Object... args) {
        String text = english;
        Map<String, String> table = TRANSLATIONS.get(language);
        if (table != null) {
            text = table.getOrDefault(english, english);
        }
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return text;
    }

    /**
     * Keeps a left-to-right snippet (an amount like "+$300.00", a rate, a code) in one piece inside
     * right-to-left text, so the bidi algorithm doesn't move its "+" or "$" to the other end.
     */
    public static String ltr(String text) {
        // LRE ... PDF (left-to-right embedding): supported by java.text.Bidi, which JavaFX uses.
        return rightToLeft() ? "‪" + text + "‬" : text;
    }

    /** For FXML: {@code text="%Show all"} looks up "Show all" through {@link #t}. */
    public static ResourceBundle bundle() {
        return new ResourceBundle() {
            @Override
            protected Object handleGetObject(String key) {
                return t(key);
            }

            /** FXMLLoader asks before reading; every key "exists" because English is the fallback. */
            @Override
            public boolean containsKey(String key) {
                return key != null;
            }

            @Override
            public Enumeration<String> getKeys() {
                return Collections.emptyEnumeration();
            }
        };
    }
}
