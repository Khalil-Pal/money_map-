package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.shape.SVGPath;

final class EditButton {
    private EditButton() {
    }

    static Button create(Runnable edit) {
        var icon = new SVGPath();
        icon.setContent("M3 11.5l-1 3.5 3.5-1L14.5 5 12 2.5zM10.5 4l2.5 2.5");
        icon.getStyleClass().addAll("icon", "edit-pencil");
        var button = new Button();
        button.getStyleClass().addAll("icon-button", "edit-button");
        button.setGraphic(icon);
        button.setAccessibleText(t("Edit"));
        button.setTooltip(new Tooltip(t("Edit")));
        button.setOnAction(event -> edit.run());
        return button;
    }
}
