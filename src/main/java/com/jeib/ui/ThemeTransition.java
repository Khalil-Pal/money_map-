package com.jeib.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/** Theme and language transitions for the main window. */
public final class ThemeTransition {

    private static final Interpolator EASE_OUT = Interpolator.SPLINE(0.05, 0.7, 0.1, 1.0);
    private static final Interpolator THEME_EASE = Interpolator.SPLINE(0.4, 0, 0.2, 1);

    private FadeTransition themeFade;

    /** Applies the new theme underneath a fading picture of the current window. */
    void crossFade(StackPane root, Runnable switchTheme) {
        if (root.getWidth() <= 0 || root.getHeight() <= 0) {
            switchTheme.run();
            return;
        }
        WritableImage before = root.snapshot(null, null);
        if (themeFade != null) {
            themeFade.stop();
            root.getChildren().remove(themeFade.getNode());
        }

        ImageView overlay = new ImageView(before);
        // The snapshot already includes the right-to-left layout.
        overlay.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        overlay.setManaged(false);
        overlay.setMouseTransparent(true);
        overlay.fitWidthProperty().bind(root.widthProperty());
        overlay.fitHeightProperty().bind(root.heightProperty());
        root.getChildren().add(overlay);

        switchTheme.run();

        themeFade = new FadeTransition(Duration.millis(360), overlay);
        // Let the new styles and layout settle before the first visible frame of the blend.
        themeFade.setDelay(Duration.millis(32));
        themeFade.setToValue(0);
        themeFade.setInterpolator(THEME_EASE);
        themeFade.setOnFinished(event -> {
            root.getChildren().remove(overlay);
            themeFade = null;
        });
        themeFade.play();
    }

    /** Fades a node out and runs {@code then}; used before the window is rebuilt in another language. */
    public static void fadeOut(Node node, Runnable then) {
        FadeTransition fade = new FadeTransition(Duration.millis(140), node);
        fade.setToValue(0);
        fade.setOnFinished(event -> then.run());
        fade.play();
    }

    /** Fades a freshly built node in. */
    public static void fadeIn(Node node) {
        node.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(240), node);
        fade.setToValue(1);
        fade.setInterpolator(EASE_OUT);
        fade.play();
    }
}
