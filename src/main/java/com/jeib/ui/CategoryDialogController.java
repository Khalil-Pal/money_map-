package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.Category;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/** Wires category-dialog.fxml to {@link CategoryDialogViewModel}. */
public final class CategoryDialogController {

    /** Names from jeib-color-system.md §5, in palette order (for tooltips and screen readers). */
    private static final String[] COLOR_NAMES = {"Orange", "Blue", "Purple", "Teal", "Pink", "Ochre", "Slate",
            "Lime", "Cyan", "Fuchsia", "Maroon", "Brown", "Plum", "Taupe"};

    private final CategoryDialogViewModel viewModel;
    private final Runnable onCancel;
    private final Consumer<String> onDone;

    @FXML private Label title;
    @FXML private TextField nameField;
    @FXML private Label nameError;
    @FXML private FlowPane colors;
    @FXML private Label colorHint;
    @FXML private Label colorError;
    @FXML private Label formError;
    @FXML private HBox confirmBox;
    @FXML private Label confirmText;
    @FXML private Button keepButton;
    @FXML private Button deleteButton;
    @FXML private Button cancelButton;
    @FXML private Button categorySave;

    private final ToggleGroup colorGroup = new ToggleGroup();

    public CategoryDialogController(CategoryDialogViewModel viewModel, Runnable onCancel, Consumer<String> onDone) {
        this.viewModel = viewModel;
        this.onCancel = onCancel;
        this.onDone = onDone;
    }

    @FXML
    private void initialize() {
        title.setText(viewModel.title());
        nameField.textProperty().bindBidirectional(viewModel.nameProperty());

        for (int i = 0; i < Category.PALETTE_SIZE; i++) {
            Region dot = new Region();
            dot.getStyleClass().addAll("color-dot", "swatch-" + i);
            ToggleButton option = new ToggleButton(null, dot);
            option.getStyleClass().add("color-option");
            option.setId("color-" + i);
            option.setUserData(i);
            option.setAccessibleText(t(COLOR_NAMES[i]));
            option.setTooltip(new Tooltip(t(COLOR_NAMES[i])));
            option.setToggleGroup(colorGroup);
            colors.getChildren().add(option);
        }
        colorGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                colorGroup.selectToggle(old); // always one color
            } else {
                viewModel.colorProperty().set((Integer) selected.getUserData());
            }
        });
        viewModel.colorProperty().addListener((obs, old, index) -> selectColor(index));
        viewModel.usageProperty().addListener((obs, old, usage) -> refreshTaken());
        selectColor(viewModel.colorProperty().get());
        refreshTaken();

        showError(nameError, "name");
        showError(colorError, "color");
        showError(formError, "form");

        // Delete: a red link first, then an explicit "Yes, delete" (it can't be undone).
        deleteButton.setVisible(viewModel.canDelete());
        deleteButton.setManaged(viewModel.canDelete());
        confirmText.setText(viewModel.deleteQuestion());
        confirmBox.visibleProperty().bind(viewModel.confirmingDeleteProperty());
        confirmBox.managedProperty().bind(viewModel.confirmingDeleteProperty());
        deleteButton.textProperty().bind(Bindings.when(viewModel.confirmingDeleteProperty())
                .then(t("Yes, delete")).otherwise(t("Delete")));
        deleteButton.disableProperty().bind(viewModel.savingProperty());
        deleteButton.setOnAction(event -> viewModel.delete(onDone));
        keepButton.setOnAction(event -> viewModel.cancelDelete());

        categorySave.disableProperty().bind(viewModel.savingProperty().or(viewModel.confirmingDeleteProperty()));
        categorySave.setOnAction(event -> viewModel.save(onDone));
        cancelButton.setOnAction(event -> onCancel.run());

        Platform.runLater(() -> {
            nameField.requestFocus();
            nameField.selectAll();
        });
    }

    private void selectColor(Integer index) {
        colorGroup.getToggles().stream()
                .filter(toggle -> Objects.equals(toggle.getUserData(), index))
                .findFirst()
                .ifPresent(colorGroup::selectToggle);
    }

    private void refreshTaken() {
        colorGroup.getToggles().forEach(toggle -> {
            int index = (Integer) toggle.getUserData();
            ((ToggleButton) toggle).setDisable(viewModel.isTaken(index));
        });
        colorHint.setText(viewModel.allColorsUsed()
                ? t("All 14 colors are in use, so colors can repeat now.")
                : t("Greyed-out colors belong to other categories."));
    }

    private void showError(Label label, String key) {
        label.textProperty().bind(viewModel.error(key));
        label.visibleProperty().bind(Bindings.isNotEmpty(viewModel.error(key)));
        label.managedProperty().bind(label.visibleProperty());
    }
}
