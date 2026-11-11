package com.jeib.ui;

import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Wires transactions.fxml to {@link TransactionsViewModel}. Holds no state of its own. */
public final class TransactionsController {

    private final TransactionsViewModel viewModel;

    @FXML private Label summary;
    @FXML private HBox header;
    @FXML private VBox rowsBox;
    @FXML private Label message;

    public TransactionsController(TransactionsViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        summary.textProperty().bind(viewModel.summaryProperty());
        message.textProperty().bind(viewModel.messageProperty());
        showOnlyWhen(message, Bindings.isNotEmpty(viewModel.messageProperty()));
        showOnlyWhen(summary, Bindings.isNotEmpty(viewModel.summaryProperty()));
        showOnlyWhen(header, Bindings.isNotEmpty(viewModel.rows()));

        viewModel.rows().addListener((ListChangeListener<TransactionRow>) change -> render());
        render();
    }

    private static void showOnlyWhen(Node node, javafx.beans.binding.BooleanBinding condition) {
        node.visibleProperty().bind(condition);
        node.managedProperty().bind(condition);
    }

    private void render() {
        rowsBox.getChildren().setAll(viewModel.rows().stream().map(TransactionsController::row).toList());
    }

    /** Not clickable yet (editing comes with the dialog), so no hover highlight: see CLAUDE.md. */
    private static Node row(TransactionRow row) {
        Label date = label(row.date(), "tx-col-date", "muted");

        Label note = row.note() == null ? label("No note", "tx-col-note", "muted") : label(row.note(), "tx-col-note");
        note.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(note, Priority.ALWAYS);

        Region swatch = new Region();
        swatch.getStyleClass().addAll("swatch", "swatch-" + row.colorIndex());
        HBox category = new HBox(swatch, new Label(row.categoryName()));
        category.getStyleClass().add("tx-col-category");

        Label amount = label(row.amount(), "tx-col-amount", row.income() ? "amount-income" : "amount");

        HBox line = new HBox(date, note, category, amount);
        line.getStyleClass().add("tx-row");
        return line;
    }

    private static Label label(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }
}
