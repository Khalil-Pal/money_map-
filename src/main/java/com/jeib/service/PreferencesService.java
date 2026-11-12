package com.jeib.service;

import com.jeib.repository.SettingsRepository;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

/** Small per-device preferences kept in the settings table (not synced): for now, the theme. */
public final class PreferencesService {

    static final String THEME_KEY = "theme";

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
}
