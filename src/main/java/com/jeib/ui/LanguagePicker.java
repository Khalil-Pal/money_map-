package com.jeib.ui;

import com.jeib.i18n.Language;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

/**
 * "English | العربية" as a segmented control, used by setup and Settings. Each language is named
 * in itself, so it's readable whatever language the app is in right now.
 */
final class LanguagePicker {

    private LanguagePicker() {
    }

    static void fill(HBox box, LanguageSwitcher languages) {
        ToggleGroup group = new ToggleGroup();
        for (Language language : Language.values()) {
            ToggleButton option = new ToggleButton(language.nativeName());
            option.setId("language-" + language.name());
            option.getStyleClass().add("segment");
            option.setUserData(language);
            option.setToggleGroup(group);
            option.setSelected(languages != null && language == languages.current());
            box.getChildren().add(option);
        }
        group.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                group.selectToggle(old);
            } else if (languages != null) {
                languages.switchTo((Language) selected.getUserData());
            }
        });
    }
}
