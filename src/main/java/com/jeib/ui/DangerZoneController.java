package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;

public final class DangerZoneController {

    private final DangerZoneViewModel viewModel;
    private final Runnable close;
    private final Runnable changed;
    private final Toasts toasts;

    @FXML private Button deleteWithoutPdf;
    @FXML private Button deleteWithPdf;
    @FXML private Button cancelReset;
    @FXML private Label resetMessage;

    DangerZoneController(DangerZoneViewModel viewModel, Runnable close, Runnable changed, Toasts toasts) {
        this.viewModel = viewModel;
        this.close = close;
        this.changed = changed;
        this.toasts = toasts;
    }

    @FXML
    private void initialize() {
        deleteWithoutPdf.disableProperty().bind(viewModel.busy);
        deleteWithPdf.disableProperty().bind(viewModel.busy);
        cancelReset.disableProperty().bind(viewModel.busy);
        resetMessage.textProperty().bind(viewModel.message);
        resetMessage.visibleProperty().bind(resetMessage.textProperty().isNotEmpty());
        resetMessage.managedProperty().bind(resetMessage.visibleProperty());
    }

    @FXML
    private void deleteOnly() {
        run(null);
    }

    @FXML
    private void exportAndDelete() {
        var picker = new FileChooser();
        picker.setTitle(t("Save the Jeib PDF export"));
        picker.setInitialFileName("Jeib-export.pdf");
        picker.getExtensionFilters().add(new FileChooser.ExtensionFilter(t("PDF documents"), "*.pdf"));
        var file = picker.showSaveDialog(deleteWithPdf.getScene().getWindow());
        if (file != null) {
            run(file.toPath());
        }
    }

    @FXML
    private void cancel() {
        close.run();
    }

    private void run(java.nio.file.Path destination) {
        viewModel.run(destination, message -> {
            close.run();
            changed.run();
            toasts.show(message);
        });
    }
}
