package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.i18n.Messages;
import com.jeib.model.BudgetPlan;
import com.jeib.service.BudgetReportService;
import com.jeib.service.BudgetReportService.Format;
import java.nio.file.Path;
import java.time.format.FormatStyle;
import java.util.Locale;
import javafx.beans.InvalidationListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;

public final class ReportsController {

    private final BudgetPlansViewModel plans;
    private final BudgetReportService reports;
    private final Toasts toasts;
    private boolean exporting;

    @FXML private ComboBox<BudgetPlan> reportPlan;
    @FXML private Label reportState;
    @FXML private Button exportExcel;
    @FXML private Button exportPdf;
    @FXML private Label reportMessage;

    public ReportsController(BudgetPlansViewModel plans, BudgetReportService reports, Toasts toasts) {
        this.plans = plans;
        this.reports = reports;
        this.toasts = toasts;
    }

    @FXML
    private void initialize() {
        reportPlan.setItems(plans.plans);
        reportPlan.setConverter(BudgetPlanLabels.converter());
        reportPlan.valueProperty().addListener((obs, before, after) -> {
            if (!exporting) {
                reportMessage.setText("");
            }
            refresh();
        });
        plans.plans.addListener((InvalidationListener) obs -> refresh());
        reportMessage.visibleProperty().bind(reportMessage.textProperty().isNotEmpty());
        reportMessage.managedProperty().bind(reportMessage.visibleProperty());
        reportMessage.setAccessibleText(t("Report export status"));
        exportExcel.setOnAction(event -> chooseDestination(Format.EXCEL));
        exportPdf.setOnAction(event -> chooseDestination(Format.PDF));
        refresh();
    }

    void refresh() {
        BudgetPlan plan = reportPlan.getValue();
        boolean ready = plan != null && reports.canExport(plan);
        exportExcel.setDisable(exporting || !ready);
        exportPdf.setDisable(exporting || !ready);
        reportPlan.setDisable(exporting || plans.plans.isEmpty());
        if (plan == null) {
            reportState.setText(plans.plans.isEmpty() ? t("No budget plans yet.") : "");
        } else if (!ready) {
            reportState.setText(t("This plan runs through {0}. Export opens the next day.",
                    Messages.date(plan.dates().end(), FormatStyle.LONG)));
        } else {
            reportState.setText(t("Budget limit: {0}", plan.currency().format(plan.limitMinor())));
        }
    }

    private void chooseDestination(Format format) {
        BudgetPlan plan = reportPlan.getValue();
        if (exporting || plan == null || !reports.canExport(plan)) {
            refresh();
            return;
        }
        String extension = format == Format.PDF ? ".pdf" : ".xlsx";
        var chooser = new FileChooser();
        chooser.setTitle(t("Save budget report"));
        chooser.setInitialFileName("Jeib-" + plan.dates().end() + extension);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                format == Format.PDF ? t("PDF documents") : t("Excel workbooks"), "*" + extension));
        var file = chooser.showSaveDialog(reportPlan.getScene().getWindow());
        if (file != null) {
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(extension)) {
                reportMessage.setText(t("Use the {0} file extension.", extension));
                return;
            }
            export(plan, format, file.toPath());
        }
    }

    private void export(BudgetPlan plan, Format format, Path destination) {
        var language = Messages.language();
        var task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                reports.export(plan.id(), language, format, destination);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            finish("");
            toasts.show(t("Report saved to {0}.", Messages.ltr(destination.toString())));
        });
        task.setOnFailed(event -> finish(t("Couldn't export the report: {0}", task.getException().getMessage())));
        exporting = true;
        reportMessage.setText(t("Exporting report…"));
        refresh();
        Thread.ofVirtual().name("jeib-report-export").start(task);
    }

    private void finish(String status) {
        exporting = false;
        reportMessage.setText(status);
        refresh();
    }
}
