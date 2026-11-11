package com.jeib.service;

/**
 * Input the user can fix: a blank name, a duplicate, a color that's taken. The message is written
 * for the user and is shown as-is in the UI.
 */
public final class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
