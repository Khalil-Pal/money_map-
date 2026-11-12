package com.jeib.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every English sentence the app shows must have an Arabic translation. Reads the sources: each
 * {@code t("...")} in Java (string pieces joined with + count as one) and each {@code %key} in FXML,
 * plus the keys passed to t() through constants.
 */
class ArabicCoverageTest {

    private static final Pattern CALL = Pattern.compile("\\bt\\(\\s*((?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*\\+?\\s*)+)");
    private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern FXML_KEY = Pattern.compile("(?:text|promptText|accessibleText)=\"%([^\"]*)\"");

    /** Keys that reach t() through a variable or constant rather than a literal. */
    private static final Set<String> INDIRECT = Set.of(
            "Undone: {0}", "Redone: {0}", "Nothing to undo.", "Nothing to redo.", "Can't undo: {0}", "Can't redo: {0}",
            "Built in Week 6: monthly report and Excel export.", "Built in Week 8: multiple accounts and currencies.",
            "Built in Week 8: recurring payments and bill reminders.", "Built in Week 8: savings goals.",
            "Orange", "Blue", "Purple", "Teal", "Pink", "Ochre", "Slate", "Lime", "Cyan", "Fuchsia", "Maroon",
            "Brown", "Plum", "Taupe", "US dollar", "Israeli shekel", "Russian ruble", "Unknown category",
            "Dashboard", "Transactions", "Budgets", "Categories", "Reports", "Accounts", "Recurring", "Goals",
            "Settings", "Food", "Rent", "Shopping", "Transport", "Entertainment", "Subscriptions", "Other", "Health",
            "Salary", "Freelance", "Other income");

    static Set<String> usedKeys() throws IOException {
        Set<String> keys = new TreeSet<>(INDIRECT);
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher call = CALL.matcher(Files.readString(file));
                while (call.find()) {
                    StringBuilder key = new StringBuilder();
                    Matcher piece = STRING.matcher(call.group(1));
                    while (piece.find()) {
                        key.append(piece.group(1).replace("\\\"", "\""));
                    }
                    keys.add(key.toString());
                }
            }
        }
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/com/jeib/ui"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".fxml")).toList()) {
                Matcher key = FXML_KEY.matcher(Files.readString(file));
                while (key.find()) {
                    keys.add(key.group(1));
                }
            }
        }
        keys.remove("");
        return keys;
    }

    @Test
    void everySentenceHasAnArabicTranslation() throws IOException {
        List<String> missing = new ArrayList<>();
        for (String key : usedKeys()) {
            if (!Arabic.TEXTS.containsKey(key)) {
                missing.add(key);
            }
        }
        assertEquals(List.of(), missing, "Add these to Arabic.java");
    }

    @Test
    void translationsKeepEveryPlaceholder() {
        List<String> broken = new ArrayList<>();
        Arabic.TEXTS.forEach((english, arabic) -> {
            for (int i = 0; i < 4; i++) {
                String placeholder = "{" + i + "}";
                if (english.contains(placeholder) != arabic.contains(placeholder)) {
                    broken.add(english);
                }
            }
        });
        assertEquals(List.of(), broken);
    }

    @Test
    void theCheckReallyFindsSentences() throws IOException {
        // Guards the regexes: if they broke, the coverage test would pass on an empty set.
        Set<String> keys = usedKeys();
        assertTrue(keys.size() > 150, "found only " + keys.size());
        assertTrue(keys.contains("Enter an amount."));
        assertTrue(keys.contains("Show all"));
    }
}
