package com.jeib.ui;

import java.time.LocalTime;

/**
 * What the user chose: always light, always dark, or automatic by the time of day (decided Nov 12,
 * 2026: dark from 7 PM to 7 AM, fixed hours). The theme actually shown is {@link #resolve}.
 */
public enum ThemeMode {
    LIGHT,
    DARK,
    AUTO;

    static final LocalTime DARK_FROM = LocalTime.of(19, 0);
    static final LocalTime LIGHT_FROM = LocalTime.of(7, 0);

    /** The theme to show at {@code now}. */
    public Theme resolve(LocalTime now) {
        return switch (this) {
            case LIGHT -> Theme.LIGHT;
            case DARK -> Theme.DARK;
            case AUTO -> now.isBefore(LIGHT_FROM) || !now.isBefore(DARK_FROM) ? Theme.DARK : Theme.LIGHT;
        };
    }

    /** The theme button cycles Light → Dark → Automatic → Light. */
    public ThemeMode next() {
        return switch (this) {
            case LIGHT -> DARK;
            case DARK -> AUTO;
            case AUTO -> LIGHT;
        };
    }

    /** A saved preference ("LIGHT", "DARK", "AUTO"); anything else is LIGHT. */
    static ThemeMode parse(String saved) {
        for (ThemeMode mode : values()) {
            if (mode.name().equals(saved)) {
                return mode;
            }
        }
        return LIGHT;
    }
}
