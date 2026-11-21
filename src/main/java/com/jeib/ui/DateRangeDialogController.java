package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.i18n.Messages;
import com.jeib.model.DateRange;
import java.time.chrono.IsoChronology;
import java.time.format.FormatStyle;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.util.converter.LocalDateStringConverter;

/** Reads the two calendar selections and applies them together. */
public final class DateRangeDialogController {

    private final DateRange initial;
    private final Runnable onCancel;
    private final Consumer<DateRange> onApply;

    @FXML private DatePicker startDate;
    @FXML private DatePicker endDate;
    @FXML private Label error;

    DateRangeDialogController(DateRange initial, Runnable onCancel, Consumer<DateRange> onApply) {
        this.initial = initial;
        this.onCancel = onCancel;
        this.onApply = onApply;
    }

    @FXML
    private void initialize() {
        localize(startDate);
        localize(endDate);
        startDate.setValue(initial.start());
        endDate.setValue(initial.end());
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());
    }

    static void localize(DatePicker picker) {
        picker.setConverter(new LocalDateStringConverter(FormatStyle.MEDIUM, Messages.locale(), IsoChronology.INSTANCE));
    }

    @FXML
    private void apply() {
        var start = startDate.getValue();
        var end = endDate.getValue();
        if (start == null || end == null || start.getYear() < 1 || end.getYear() > 9999) {
            error.setText(t("Choose a valid start and end date."));
            return;
        }
        if (start.isAfter(end)) {
            error.setText(t("The end date must be on or after the start date."));
            return;
        }
        onApply.accept(new DateRange(start, end));
    }

    @FXML
    private void cancel() {
        onCancel.run();
    }
}
