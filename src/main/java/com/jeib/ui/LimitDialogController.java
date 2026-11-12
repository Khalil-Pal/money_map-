package com.jeib.ui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/** Wires limit-dialog.fxml to {@link LimitDialogViewModel}. */
public final class LimitDialogController {

    private final LimitDialogViewModel viewModel;
    private final Runnable onCancel;
    private final Runnable onDone;
    private final String currencyCode;

    @FXML private Label title;
    @FXML private Label hint;
    @FXML private TextField limitField;
    @FXML private Label unit;
    @FXML private Label error;
    @FXML private Button removeButton;
    @FXML private Button cancelButton;
    @FXML private Button limitSave;

    public LimitDialogController(LimitDialogViewModel viewModel, String currencyCode, Runnable onCancel,
                                 Runnable onDone) {
        this.viewModel = viewModel;
        this.onCancel = onCancel;
        this.onDone = onDone;
        this.currencyCode = currencyCode;
    }

    @FXML
    private void initialize() {
        title.setText(viewModel.title());
        hint.setText(viewModel.hint());
        unit.setText(currencyCode);
        limitField.textProperty().bindBidirectional(viewModel.amountProperty());
        error.textProperty().bind(viewModel.errorProperty());
        error.visibleProperty().bind(Bindings.isNotEmpty(viewModel.errorProperty()));
        error.managedProperty().bind(error.visibleProperty());

        removeButton.setVisible(viewModel.hasLimit());
        removeButton.setManaged(viewModel.hasLimit());
        removeButton.disableProperty().bind(viewModel.savingProperty());
        removeButton.setOnAction(event -> viewModel.remove(onDone));
        limitSave.disableProperty().bind(viewModel.savingProperty());
        limitSave.setOnAction(event -> viewModel.save(onDone));
        cancelButton.setOnAction(event -> onCancel.run());

        Platform.runLater(() -> {
            limitField.requestFocus();
            limitField.selectAll();
        });
    }
}
