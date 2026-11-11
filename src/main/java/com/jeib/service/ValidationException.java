package com.jeib.service;

/**
 * Input the user can fix: a blank name, a duplicate, a bad amount. The message is written for the
 * user and is shown as-is in the UI.
 */
public final class ValidationException extends RuntimeException {

    private final String field;

    public ValidationException(String message) {
        this(null, message);
    }

    /**
     * @param field the form field the problem is in ("amount", "date", ...), so a dialog can
     *              highlight it; null when it isn't about one field
     */
    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** The form field the problem is in, or null. */
    public String field() {
        return field;
    }
}
