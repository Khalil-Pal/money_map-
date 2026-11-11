package com.jeib.ui;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Short messages at the bottom of the window ("Transaction deleted · Undo") that disappear by
 * themselves. One at a time: a new message replaces the current one.
 */
public final class Toasts {

    static final Duration VISIBLE = Duration.seconds(6);

    private StackPane layer;
    private final PauseTransition timer = new PauseTransition(VISIBLE);

    /** Called once by MainController with the layer above everything else. */
    void attach(StackPane layer) {
        this.layer = layer;
        layer.setPickOnBounds(false); // clicks on the empty part reach the app below
        timer.setOnFinished(event -> hide());
    }

    public void show(String message) {
        show(message, null, null);
    }

    /** A message with one action button, e.g. "Undo". The button also hides the toast. */
    public void show(String message, String actionText, Runnable action) {
        Label text = new Label(message);
        text.getStyleClass().add("toast-text");
        HBox toast = new HBox(text);
        toast.getStyleClass().add("toast");
        toast.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        if (action != null) {
            Button button = new Button(actionText);
            button.setId("toastAction");
            button.getStyleClass().add("toast-action");
            button.setOnAction(event -> {
                hide();
                action.run();
            });
            toast.getChildren().add(button);
        }
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(0, 0, 28, 0));
        layer.getChildren().setAll(toast);
        timer.playFromStart();
    }

    public void hide() {
        timer.stop();
        layer.getChildren().clear();
    }
}
