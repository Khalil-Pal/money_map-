package com.jeib.ui;

import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Wires transactions.fxml to {@link TransactionsViewModel}. Holds no state of its own. */
public final class TransactionsController {

    private final TransactionsViewModel viewModel;
    private final Dialogs dialogs;
    private final Toasts toasts;
    private final HistoryViewModel history;

    @FXML private HBox filterBar;
    @FXML private HBox filterChip;
    @FXML private Button showAll;
    @FXML private Label summary;
    @FXML private HBox header;
    @FXML private VBox rowsBox;
    @FXML private Label message;

    public TransactionsController(TransactionsViewModel viewModel, Dialogs dialogs, Toasts toasts,
                                  HistoryViewModel history) {
        this.viewModel = viewModel;
        this.dialogs = dialogs;
        this.toasts = toasts;
        this.history = history;
    }

    @FXML
    private void initialize() {
        summary.textProperty().bind(viewModel.summaryProperty());
        message.textProperty().bind(viewModel.messageProperty());
        showOnlyWhen(message, Bindings.isNotEmpty(viewModel.messageProperty()));
        showOnlyWhen(summary, Bindings.isNotEmpty(viewModel.summaryProperty()));
        showOnlyWhen(header, Bindings.isNotEmpty(viewModel.rows()));

        showOnlyWhen(filterBar, viewModel.filterProperty().isNotNull());
        viewModel.filterProperty().addListener((obs, old, category) -> {
            if (category != null) {
                Region dot = new Region();
                dot.getStyleClass().addAll("swatch", "swatch-" + category.colorIndex());
                filterChip.getChildren().setAll(dot, new Label(category.name()));
            }
        });
        showAll.setOnAction(event -> viewModel.filterBy(null));

        viewModel.rows().addListener((ListChangeListener<TransactionRow>) change -> render());
        render();
    }

    private static void showOnlyWhen(Node node, javafx.beans.binding.BooleanBinding condition) {
        node.visibleProperty().bind(condition);
        node.managedProperty().bind(condition);
    }

    private void render() {
        rowsBox.getChildren().setAll(viewModel.rows().stream().map(this::row).toList());
    }

    /**
     * A clickable row: click it, or focus it with Tab and press Enter or Space, to edit. Delete
     * (or Backspace) on a focused row deletes it, with Undo in the toast.
     */
    private Node row(TransactionRow row) {
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
        line.getStyleClass().addAll("tx-row", "tx-row-clickable");
        line.setFocusTraversable(true);
        line.setOnMouseClicked(event -> dialogs.openEditTransaction(row.source()));
        line.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                dialogs.openEditTransaction(row.source());
                event.consume();
            } else if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                viewModel.delete(row.id(), () -> toasts.show("Transaction deleted.", "Undo", history::undo));
                event.consume();
            }
        });
        return line;
    }

    private static Label label(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }
}
