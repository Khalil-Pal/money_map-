package com.jeib.db;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * How timestamps are stored: ISO-8601 UTC with exactly three decimals, e.g. 2026-10-25T09:30:00.000Z.
 *
 * <p>The fixed width matters. Sync compares updated_at values as text, and text only sorts like time
 * when every value has the same shape: "…:00Z" sorts after "…:00.123Z" ('Z' comes after '.'), even
 * though it's 123 ms earlier. {@code Instant.toString()} drops or varies the decimals, so it isn't
 * used for storage.
 */
public final class Timestamps {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private Timestamps() {
    }

    public static String format(Instant instant) {
        return FORMAT.format(instant);
    }

    /** Accepts any ISO-8601 instant, including the second-precision ones written by migration V2. */
    public static Instant parse(String text) {
        return Instant.parse(text);
    }
}
