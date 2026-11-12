package com.jeib.ui;

import com.jeib.model.Category;
import com.jeib.model.TransactionType;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Wires categories.fxml to {@link CategoriesViewModel}. Rows open the category dialog. */
public final class CategoriesController {

    private final CategoriesViewModel viewModel;
    private final Dialogs dialogs;

    @FXML private Label message;
    @FXML private VBox expenseRows;
    @FXML private VBox incomeRows;
    @FXML private Button newExpense;
    @FXML private Button newIncome;

    public CategoriesController(CategoriesViewModel viewModel, Dialogs dialogs) {
        this.viewModel = viewModel;
        this.dialogs = dialogs;
    }

    @FXML
    private void initialize() {
        message.textProperty().bind(viewModel.messageProperty());
        message.visibleProperty().bind(Bindings.isNotEmpty(viewModel.messageProperty()));
        message.managedProperty().bind(message.visibleProperty());

        newExpense.setOnAction(event -> dialogs.openCategory(null, TransactionType.EXPENSE));
        newIncome.setOnAction(event -> dialogs.openCategory(null, TransactionType.INCOME));

        viewModel.expense().addListener((ListChangeListener<Category>) change -> render(expenseRows, viewModel.expense()));
        viewModel.income().addListener((ListChangeListener<Category>) change -> render(incomeRows, viewModel.income()));
        render(expenseRows, viewModel.expense());
        render(incomeRows, viewModel.income());
    }

    private void render(VBox box, ObservableList<Category> categories) {
        box.getChildren().setAll(categories.stream().map(this::row).toList());
    }

    private Node row(Category category) {
        Region swatch = new Region();
        swatch.getStyleClass().addAll("swatch", "swatch-" + category.colorIndex());
        Label name = new Label(category.name());
        HBox row = new HBox(10, swatch, name);
        if (category.builtIn()) {
            Label note = new Label("built-in · can't be deleted");
            note.getStyleClass().add("chip-note");
            row.getChildren().add(note);
        }
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        Label edit = new Label("Edit");
        edit.getStyleClass().add("row-action");
        row.getChildren().addAll(gap, edit);
        row.getStyleClass().addAll("tx-row", "tx-row-clickable", "category-row");
        row.setFocusTraversable(true);
        row.setOnMouseClicked(event -> dialogs.openCategory(category, category.kind()));
        row.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                dialogs.openCategory(category, category.kind());
                event.consume();
            }
        });
        return row;
    }
}
