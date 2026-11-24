package com.jeib.service;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Exports use the app's light palette so the printed colors match the on-screen charts. */
final class ReportColors {
    private static final Pattern TOKEN = Pattern.compile("(-[a-z0-9-]+):\\s*(#[0-9a-fA-F]{6});");
    private final Map<String, Color> colors = new HashMap<>();

    ReportColors() throws IOException {
        try (InputStream input = ReportColors.class.getResourceAsStream("/com/jeib/ui/styles/theme-light.css")) {
            var entries = TOKEN.matcher(new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8));
            while (entries.find()) {
                colors.put(entries.group(1), Color.decode(entries.group(2)));
            }
        }
    }

    Color get(String key) {
        return Objects.requireNonNull(colors.get("-" + key), key);
    }

    Color category(int index) {
        return get("chart-" + index);
    }
}
