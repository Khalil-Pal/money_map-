package com.jeib;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jeib.db.Database;
import com.jeib.i18n.Language;
import com.jeib.repository.SettingsRepository;
import com.jeib.service.PreferencesService;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JeibAppTest {

    @TempDir Path folder;

    @Test
    void aSavedChoiceOverridesTheWindowsDisplayLanguage() throws Exception {
        Locale previous = Locale.getDefault(Locale.Category.DISPLAY);
        var database = new Database(folder.resolve("jeib.db"));
        try {
            Locale.setDefault(Locale.Category.DISPLAY, Locale.forLanguageTag("ru-RU"));
            assertEquals(Language.RUSSIAN, JeibApp.savedLanguage(database));
            database.migrate();
            new PreferencesService(new SettingsRepository(database)).setLanguage(Language.ARABIC);
            assertEquals(Language.ARABIC, JeibApp.savedLanguage(database));
        } finally {
            Locale.setDefault(Locale.Category.DISPLAY, previous);
        }
    }
}
