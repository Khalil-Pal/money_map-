package com.jeib.ui;

import com.jeib.model.LimitStatus;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/**
 * The limit status label from jeib-color-system.md §4: a 20 px icon tile plus a label, never color
 * alone (color-blind safe). Icons are Lucide paths on a 24 px grid, drawn at 12 px.
 */
final class StatusTile {

    private static final String CHECK = "M20 6L9 17l-5-5";
    private static final String TRIANGLE = "M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86"
            + "a2 2 0 0 0-3.42 0zM12 9v4M12 17h.01";
    private static final String OCTAGON = "M7.86 2h8.28L22 7.86v8.28L16.14 22H7.86L2 16.14V7.86L7.86 2zM12 8v4M12 16h.01";
    private static final String DASH = "M5 12h14";

    private StatusTile() {
    }

    static Node of(LimitStatus status) {
        String path;
        String text;
        String style;
        switch (status) {
            case OK -> { path = CHECK; text = "Within limit"; style = "status-ok"; }
            case WARNING -> { path = TRIANGLE; text = "Near limit"; style = "status-warning"; }
            case EXCEEDED -> { path = OCTAGON; text = "Limit exceeded"; style = "status-exceeded"; }
            default -> { path = DASH; text = "No limit"; style = "status-none"; }
        }
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("status-icon");
        icon.setScaleX(0.5);
        icon.setScaleY(0.5);
        StackPane tile = new StackPane(new Group(icon));
        tile.getStyleClass().add("status-tile");
        Label label = new Label(text);
        label.getStyleClass().add("status-label");
        HBox box = new HBox(tile, label);
        box.getStyleClass().addAll("status", style);
        return box;
    }
}
