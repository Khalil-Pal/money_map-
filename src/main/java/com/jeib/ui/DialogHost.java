package com.jeib.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

/**
 * Shows one dialog at a time inside the main window, over a dimmed backdrop (not as a separate OS
 * window), so dialogs always follow the current theme. While a dialog is open, the window behind it
 * is disabled: no clicks or keyboard focus can reach it.
 */
public final class DialogHost {

    private StackPane layer;
    private Node behind;

    /** Called once by MainController with the overlay layer and the app content behind it. */
    void attach(StackPane layer, Node behind) {
        this.layer = layer;
        this.behind = behind;
        layer.setVisible(false);
    }

    public boolean isOpen() {
        return layer != null && layer.isVisible();
    }

    /** Opens {@code dialog}, replacing nothing: callers check {@link #isOpen()} first. */
    public void show(Parent dialog) {
        StackPane.setAlignment(dialog, Pos.CENTER);
        layer.getChildren().setAll(dialog);
        layer.setVisible(true);
        behind.setDisable(true);
    }

    public void close() {
        layer.getChildren().clear();
        layer.setVisible(false);
        behind.setDisable(false);
    }
}
