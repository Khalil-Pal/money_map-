package com.jeib.service;

import com.jeib.i18n.Language;
import com.jeib.repository.SettingsRepository;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/** Small per-device preferences kept in the settings table (not synced): theme and language. */
public final class PreferencesService {

    static final String THEME_KEY = "theme";
    static final String LANGUAGE_KEY = "language";

    private final SettingsRepository settings;

    public PreferencesService(SettingsRepository settings) {
        this.settings = settings;
    }

    /** The saved theme name ("LIGHT" or "DARK"), or empty if the user never chose one. */
    public Optional<String> theme() throws SQLException {
        return settings.get(THEME_KEY);
    }

    public void setTheme(String name) throws SQLException {
        settings.save(Map.of(THEME_KEY, name));
    }

    /** The saved language, or empty if none was chosen (or the saved value is unknown). */
    public Optional<Language> language() throws SQLException {
        return settings.get(LANGUAGE_KEY).flatMap(name -> Arrays.stream(Language.values())
                .filter(language -> language.name().equals(name))
                .findFirst());
    }

    public void setLanguage(Language language) throws SQLException {
        settings.save(Map.of(LANGUAGE_KEY, language.name()));
    }
}
