package com.jeib.model;

/**
 * Arabic keyboards type Arabic-Indic digits (٢٥٠٫٥) and Persian keyboards Extended Arabic-Indic
 * ones (۲۵۰). Amounts, rates, and quick-add accept them by turning them into ASCII first.
 */
public final class Digits {

    private Digits() {
    }

    /** "٢٥٠٫٥" → "250.5"; ASCII text is returned unchanged. Null stays null. */
    public static String normalize(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '٠' && c <= '٩') {
                out.append((char) ('0' + (c - '٠')));   // Arabic-Indic
            } else if (c >= '۰' && c <= '۹') {
                out.append((char) ('0' + (c - '۰')));   // Extended Arabic-Indic (Persian, Urdu)
            } else if (c == '٫') {
                out.append('.');                              // Arabic decimal separator ٫
            } else if (c == '٬') {
                continue;                                     // Arabic thousands separator ٬
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
