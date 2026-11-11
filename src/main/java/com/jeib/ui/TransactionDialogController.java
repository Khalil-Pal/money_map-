package com.jeib.ui;

import com.jeib.model.Category;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.TransactionType;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Wires transaction-dialog.fxml to {@link TransactionDialogViewModel}. Holds no state of its own. */
public final class TransactionDialogController {

    private final TransactionDialogViewModel viewModel;
    private final Runnable onCancel;
    private final Runnable onSaved;

    @FXML private Label title;
    @FXML private ToggleButton typeExpense;
    @FXML private ToggleButton typeIncome;
    @FXML private TextField amountField;
    @FXML private HBox currencyBox;
    @FXML private VBox rateRow;
    @FXML private Label rateLabel;
    @FXML private TextField rateField;
    @FXML private Label rateUnit;
    @FXML private DatePicker datePicker;
    @FXML private FlowPane categoryChips;
    @FXML private TextField noteField;
    @FXML private Label typeError;
    @FXML private Label amountError;
    @FXML private Label currencyError;
    @FXML private Label rateError;
    @FXML private Label dateError;
    @FXML private Label categoryError;
    @FXML private Label noteError;
    @FXML private Label formError;
    @FXML private Button cancelButton;
    @FXML private Button saveButton;

    private final ToggleGroup typeGroup = new ToggleGroup();
    private final ToggleGroup currencyGroup = new ToggleGroup();
    private final ToggleGroup categoryGroup = new ToggleGroup();

    public TransactionDialogController(TransactionDialogViewModel viewModel, Runnable onCancel, Runnable onSaved) {
        this.viewModel = viewModel;
        this.onCancel = onCancel;
        this.onSaved = onSaved;
    }

    @FXML
    private void initialize() {
        title.textProperty().bind(viewModel.titleProperty());

        typeExpense.setUserData(TransactionType.EXPENSE);
        typeIncome.setUserData(TransactionType.INCOME);
        typeExpense.setToggleGroup(typeGroup);
        typeIncome.setToggleGroup(typeGroup);
        syncToggles(typeGroup, viewModel.typeProperty());

        for (SupportedCurrency currency : SupportedCurrency.values()) {
            ToggleButton option = new ToggleButton(currency.symbol() + "  " + currency.name());
            option.setId("currency-" + currency.name());
            option.getStyleClass().add("segment");
            option.setUserData(currency);
            option.setToggleGroup(currencyGroup);
            currencyBox.getChildren().add(option);
        }
        syncToggles(currencyGroup, viewModel.currencyProperty());

        amountField.textProperty().bindBidirectional(viewModel.amountProperty());
        rateField.textProperty().bindBidirectional(viewModel.rateProperty());
        noteField.textProperty().bindBidirectional(viewModel.noteProperty());
        datePicker.valueProperty().bindBidirectional(viewModel.dateProperty());

        rateLabel.textProperty().bind(viewModel.rateLabel());
        rateUnit.textProperty().bind(Bindings.createStringBinding(
                () -> Objects.toString(viewModel.mainCurrencyProperty().get(), ""), viewModel.mainCurrencyProperty()));
        rateRow.visibleProperty().bind(viewModel.rateNeeded());
        rateRow.managedProperty().bind(viewModel.rateNeeded());

        viewModel.categoriesForType().addListener((ListChangeListener<Category>) change -> renderCategories());
        viewModel.categoryIdProperty().addListener((obs, old, id) -> selectCategory(id));
        categoryGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                viewModel.categoryIdProperty().set((String) selected.getUserData());
            }
        });
        renderCategories();

        showError(typeError, "type");
        showError(amountError, "amount");
        showError(currencyError, "currency");
        showError(rateError, "rate");
        showError(dateError, "date");
        showError(categoryError, "category");
        showError(noteError, "note");
        showError(formError, TransactionDialogViewModel.FORM);

        saveButton.disableProperty().bind(viewModel.savingProperty());
        saveButton.setOnAction(event -> viewModel.save(onSaved));
        cancelButton.setOnAction(event -> onCancel.run());

        Platform.runLater(amountField::requestFocus);
    }

    /**
     * Keeps a toggle group and a property in step both ways. Clicking the selected option again
     * would leave nothing selected; that's undone, so there's always a choice.
     */
    private static <T> void syncToggles(ToggleGroup group, ObjectProperty<T> property) {
        group.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                group.selectToggle(old);
            } else {
                @SuppressWarnings("unchecked")
                T value = (T) selected.getUserData();
                property.set(value);
            }
        });
        property.addListener((obs, old, value) -> select(group, value));
        select(group, property.get());
    }

    private static void select(ToggleGroup group, Object value) {
        for (Toggle toggle : group.getToggles()) {
            if (Objects.equals(toggle.getUserData(), value)) {
                group.selectToggle(toggle);
                return;
            }
        }
    }

    private void renderCategories() {
        categoryGroup.getToggles().clear();
        categoryChips.getChildren().setAll(viewModel.categoriesForType().stream().map(category -> {
            Region swatch = new Region();
            swatch.getStyleClass().addAll("swatch", "swatch-" + category.colorIndex());
            ToggleButton chip = new ToggleButton(category.name(), swatch);
            chip.getStyleClass().add("category-chip");
            chip.setUserData(category.id());
            chip.setToggleGroup(categoryGroup);
            return chip;
        }).toList());
        selectCategory(viewModel.categoryIdProperty().get());
    }

    private void selectCategory(String id) {
        if (id == null) {
            categoryGroup.selectToggle(null);
        } else {
            select(categoryGroup, id);
        }
    }

    private void showError(Label label, String key) {
        label.textProperty().bind(viewModel.error(key));
        label.visibleProperty().bind(Bindings.isNotEmpty(viewModel.error(key)));
        label.managedProperty().bind(label.visibleProperty());
    }
}
