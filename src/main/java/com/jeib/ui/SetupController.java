package com.jeib.ui;

import com.jeib.model.SupportedCurrency;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Wires setup.fxml to {@link SetupViewModel}. One big option per supported currency. */
public final class SetupController {

    private final SetupViewModel viewModel;
    private final Runnable onDone;

    @FXML private HBox choices;
    @FXML private Label error;
    @FXML private Button continueButton;

    public SetupController(SetupViewModel viewModel, Runnable onDone) {
        this.viewModel = viewModel;
        this.onDone = onDone;
    }

    @FXML
    private void initialize() {
        ToggleGroup group = new ToggleGroup();
        for (SupportedCurrency currency : SupportedCurrency.values()) {
            Label symbol = new Label(currency.symbol());
            symbol.getStyleClass().add("currency-option-symbol");
            Label code = new Label(currency.name());
            code.getStyleClass().add("currency-option-code");
            Label name = new Label(currency.displayName());
            name.getStyleClass().add("muted");
            ToggleButton option = new ToggleButton();
            option.setGraphic(new VBox(2, symbol, code, name));
            option.setId("setup-" + currency.name());
            option.getStyleClass().add("currency-option");
            option.setUserData(currency);
            option.setToggleGroup(group);
            option.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(option, Priority.ALWAYS);
            choices.getChildren().add(option);
        }
        group.selectedToggleProperty().addListener((obs, old, selected) -> viewModel.choiceProperty()
                .set(selected == null ? null : (SupportedCurrency) selected.getUserData()));

        error.textProperty().bind(viewModel.errorProperty());
        error.visibleProperty().bind(Bindings.isNotEmpty(viewModel.errorProperty()));
        error.managedProperty().bind(error.visibleProperty());

        continueButton.disableProperty().bind(viewModel.canSave().not());
        continueButton.setOnAction(event -> viewModel.save(onDone));
    }
}
