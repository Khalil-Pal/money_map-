package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.i18n.Messages;
import com.jeib.model.Transaction;
import java.text.Bidi;
import java.time.LocalDate;
import java.time.format.FormatStyle;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

final class CalendarDayDialog extends VBox {

    private final Button closeButton = new Button(t("Close"));
    private final VBox content = new VBox(10);
    private final Consumer<Transaction> edit;

    CalendarDayDialog(LocalDate date, ObservableValue<List<TransactionRow>> rows,
                      ObservableValue<String> error, Runnable close, Consumer<Transaction> edit) {
        this.edit = edit;
        getStyleClass().add("dialog");
        setMaxSize(490, Region.USE_PREF_SIZE);

        var title = new Label(Messages.date(date, FormatStyle.FULL));
        title.getStyleClass().add("dialog-title");
        title.setWrapText(true);
        closeButton.getStyleClass().addAll("button-secondary", "calendar-day-close");
        closeButton.setCancelButton(true);
        closeButton.setOnAction(event -> close.run());
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var heading = new HBox(12, title, spacer, closeButton);
        heading.setAlignment(Pos.CENTER_LEFT);

        var scroll = new ScrollPane(content);
        scroll.getStyleClass().add("calendar-day-list");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMaxHeight(350);
        getChildren().addAll(heading, scroll);

        InvalidationListener update = obs -> render(rows.getValue(), error.getValue());
        sceneProperty().addListener((obs, before, after) -> {
            if (after == null) {
                rows.removeListener(update);
                error.removeListener(update);
            } else if (before == null) {
                rows.addListener(update);
                error.addListener(update);
                update.invalidated(rows);
            }
        });
    }

    void focusClose() {
        closeButton.requestFocus();
    }

    private void render(List<TransactionRow> rows, String error) {
        if (rows == null) {
            showMessage(error.isEmpty() ? t("Loading transactions…") : error);
        } else if (rows.isEmpty()) {
            showMessage(t("No transactions on this day."));
        } else {
            content.getChildren().setAll(groupByCategory(rows).stream().map(this::category).toList());
        }
    }

    private void showMessage(String text) {
        var message = new Label(text);
        message.setWrapText(true);
        message.getStyleClass().add("muted");
        content.getChildren().setAll(message);
    }

    static List<List<TransactionRow>> groupByCategory(List<TransactionRow> rows) {
        var groups = rows.stream().collect(Collectors.groupingBy(
                row -> row.source().categoryId(), LinkedHashMap::new, Collectors.toList()));
        return groups.values().stream()
                .sorted(Comparator.comparing(group -> group.getFirst().fallbackCategory()))
                .toList();
    }

    private VBox category(List<TransactionRow> rows) {
        var first = rows.getFirst();
        var swatch = new Region();
        swatch.getStyleClass().addAll("swatch", "swatch-" + first.colorIndex());
        var name = new Label(first.categoryName());
        name.setWrapText(true);
        var heading = new HBox(8, swatch, name);
        heading.getStyleClass().add("calendar-day-category");
        var section = new VBox(heading);
        rows.forEach(row -> section.getChildren().add(transaction(row)));
        return section;
    }

    private HBox transaction(TransactionRow row) {
        boolean hasNote = row.note() != null && !row.note().isBlank();
        var note = new Label(hasNote ? row.note() : t("No note"));
        if (hasNote) {
            var direction = new Bidi(row.note(), Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
            note.setNodeOrientation(direction.baseIsLeftToRight()
                    ? NodeOrientation.LEFT_TO_RIGHT : NodeOrientation.RIGHT_TO_LEFT);
        } else {
            note.getStyleClass().add("muted");
        }
        note.setWrapText(true);
        note.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(note, Priority.ALWAYS);
        var amount = new Label(row.amount());
        amount.getStyleClass().add(row.income() ? "amount-income" : "amount");
        amount.setMinWidth(Region.USE_PREF_SIZE);
        var entry = new HBox(12, note, amount, EditButton.create(() -> edit.accept(row.source())));
        entry.getStyleClass().add("calendar-transaction");
        return entry;
    }
}
