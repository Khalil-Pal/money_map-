package com.jeib.ui;

import com.jeib.i18n.Language;
import com.jeib.i18n.Messages;
import com.jeib.service.PreferencesService;
import java.util.function.Consumer;
import javafx.concurrent.Task;

/**
 * Changes the app's language: saves the choice in the background, then asks the app to rebuild
 * its window in that language (every text and the left/right direction come from {@link Messages}
 * when the window is built, so a rebuild is the simplest way to switch everything at once).
 */
public final class LanguageSwitcher {

    private final PreferencesService preferences;
    private final Consumer<Language> rebuildIn;

    /** @param rebuildIn called on the JavaFX thread after saving; JeibApp rebuilds the window */
    public LanguageSwitcher(PreferencesService preferences, Consumer<Language> rebuildIn) {
        this.preferences = preferences;
        this.rebuildIn = rebuildIn;
    }

    public Language current() {
        return Messages.language();
    }

    public void switchTo(Language language) {
        if (language == Messages.language()) {
            return;
        }
        Task<Void> save = new Task<>() {
            @Override
            protected Void call() throws Exception {
                preferences.setLanguage(language);
                return null;
            }
        };
        save.setOnSucceeded(event -> rebuildIn.accept(language));
        // Even if saving fails, switch now; it just won't be remembered.
        save.setOnFailed(event -> rebuildIn.accept(language));
        Thread.ofVirtual().name("jeib-language").start(save);
    }
}
