package com.jeib.ui;

import java.util.Objects;
import javafx.scene.Scene;

/**
 * Light and dark themes. Each theme file only defines the color tokens from jeib-color-system.md;
 * app.css styles every component with those tokens, so switching themes is swapping one file.
 */
public enum Theme {
    LIGHT("styles/theme-light.css"),
    DARK("styles/theme-dark.css");

    private static final String COMPONENTS = "styles/app.css";

    private final String stylesheet;

    Theme(String stylesheet) {
        this.stylesheet = stylesheet;
    }

    public Theme toggled() {
        return this == LIGHT ? DARK : LIGHT;
    }

    public void applyTo(Scene scene) {
        scene.getStylesheets().setAll(url(stylesheet), url(COMPONENTS));
    }

    private static String url(String path) {
        return Objects.requireNonNull(Theme.class.getResource(path), path).toExternalForm();
    }
}
