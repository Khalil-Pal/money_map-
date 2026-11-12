package com.jeib.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Bounds;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

/**
 * The motion when the theme changes: the new theme spreads out as a circle from the button that was
 * clicked, over a picture of the old one. How it works: take a snapshot of the window, lay it on
 * top, switch the stylesheets underneath, then cut an ever-larger circular hole into the snapshot.
 * No layout is redone during the animation, so it stays smooth.
 */
public final class ThemeTransition {

    /** Long enough to see, short enough not to wait for (a common range is 300-500 ms). */
    static final Duration REVEAL = Duration.millis(520);
    /** Material's "emphasized decelerate": fast start, gentle stop. */
    static final Interpolator EASE_OUT = Interpolator.SPLINE(0.05, 0.7, 0.1, 1.0);

    private ThemeTransition() {
    }

    /**
     * @param root    the window's root (the overlay is added to it and removed at the end)
     * @param origin  where the circle starts (the theme button), or null for the window's center
     * @param switchTheme applies the new stylesheets; runs right after the snapshot is taken
     */
    static void reveal(StackPane root, Node origin, Runnable switchTheme) {
        if (root.getScene() == null || root.getWidth() <= 0) {
            switchTheme.run();
            return;
        }
        WritableImage before = root.snapshot(null, null);
        ImageView overlay = new ImageView(before);
        // The root may be right-to-left (mirrored); the picture must not be mirrored a second time.
        overlay.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        overlay.setManaged(false);
        overlay.setMouseTransparent(true);
        root.getChildren().add(overlay);

        switchTheme.run();

        double width = before.getWidth();
        double height = before.getHeight();
        Point2D center = new Point2D(width / 2, height / 2);
        if (origin != null && origin.getScene() != null) {
            Bounds bounds = origin.localToScene(origin.getBoundsInLocal());
            center = overlay.sceneToLocal(bounds.getCenterX(), bounds.getCenterY());
        }
        double farthest = Math.max(Math.max(center.distance(0, 0), center.distance(width, 0)),
                Math.max(center.distance(0, height), center.distance(width, height)));

        Point2D from = center;
        DoubleProperty radius = new SimpleDoubleProperty(0);
        radius.addListener((obs, old, r) -> overlay.setClip(
                Shape.subtract(new Rectangle(width, height), new Circle(from.getX(), from.getY(), r.doubleValue()))));
        Timeline grow = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(radius, 0)),
                new KeyFrame(REVEAL, new KeyValue(radius, farthest, EASE_OUT)));
        grow.setOnFinished(event -> root.getChildren().remove(overlay));
        grow.play();
    }

    /** The theme button's icon turns into the next one: a quarter turn back, then into place. */
    static void spin(Node icon) {
        icon.setRotate(-90);
        icon.setScaleX(0.6);
        icon.setScaleY(0.6);
        RotateTransition turn = new RotateTransition(Duration.millis(420), icon);
        turn.setToAngle(0);
        turn.setInterpolator(EASE_OUT);
        ScaleTransition grow = new ScaleTransition(Duration.millis(420), icon);
        grow.setToX(1);
        grow.setToY(1);
        grow.setInterpolator(EASE_OUT);
        new ParallelTransition(turn, grow).play();
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
