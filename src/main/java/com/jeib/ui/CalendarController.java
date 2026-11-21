package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.i18n.Messages;
import com.jeib.model.DateRange;
import com.jeib.service.BudgetService.CategorySpending;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Collectors;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

public final class CalendarController {

    private final CalendarViewModel viewModel;
    private final Dialogs dialogs;

    @FXML private Button previousYear;
    @FXML private Button nextYear;
    @FXML private Label calendarYear;
    @FXML private TilePane months;
    @FXML private Button historyDateRange;
    @FXML private Button returnToMonth;
    @FXML private Label period;
    @FXML private Label message;
    @FXML private VBox spending;
    @FXML private VBox rows;
    private int displayedYear;

    public CalendarController(CalendarViewModel viewModel, Dialogs dialogs) {
        this.viewModel = viewModel;
        this.dialogs = dialogs;
    }

    @FXML
    private void initialize() {
        message.textProperty().bind(viewModel.message);
        message.visibleProperty().bind(message.textProperty().isNotEmpty());
        message.managedProperty().bind(message.visibleProperty());
        previousYear.setOnAction(event -> showYear(displayedYear - 1));
        nextYear.setOnAction(event -> showYear(displayedYear + 1));
        historyDateRange.disableProperty().bind(viewModel.month.isNull());
        historyDateRange.setOnAction(event -> dialogs.openDateRange(viewModel.selectedDates(), viewModel.customRange::set));
        returnToMonth.visibleProperty().bind(viewModel.customRange.isNotNull());
        returnToMonth.managedProperty().bind(returnToMonth.visibleProperty());
        returnToMonth.setOnAction(event -> viewModel.customRange.set(null));
        viewModel.month.addListener((obs, before, after) -> {
            showYear(after.getYear());
            showPeriod();
        });
        viewModel.customRange.addListener((obs, before, after) -> {
            showYear(after == null ? viewModel.month.get().getYear() : displayedYear);
            showPeriod();
        });
        viewModel.history.addListener((obs, before, after) -> render(after));
        previousYear.setDisable(true);
        nextYear.setDisable(true);
    }

    private void showYear(int year) {
        displayedYear = year;
        calendarYear.setText(Integer.toString(year));
        YearMonth first = viewModel.firstMonth.get();
        YearMonth current = viewModel.currentMonth();
        previousYear.setDisable(year <= first.getYear());
        nextYear.setDisable(year >= current.getYear());
        DateTimeFormatter name = DateTimeFormatter.ofPattern("LLLL", Messages.locale());
        months.getChildren().clear();
        for (int number = 1; number <= 12; number++) {
            YearMonth month = YearMonth.of(year, number);
            if (month.isBefore(first) || month.isAfter(current)) {
                continue;
            }
            boolean selected = month.equals(viewModel.month.get()) && viewModel.customRange.get() == null;
            Button button = new Button(name.format(month));
            button.setId("calendar-month-" + number);
            button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add(selected ? "button-primary" : "button-secondary");
            button.setOnAction(event -> viewModel.selectMonth(month));
            months.getChildren().add(button);
        }
    }

    private void showPeriod() {
        DateRange custom = viewModel.customRange.get();
        period.setText(custom == null ? DateTimeFormatter.ofPattern("LLLL yyyy", Messages.locale())
                .format(viewModel.month.get()) : Messages.dateRange(custom));
    }

    private void render(CalendarViewModel.History history) {
        spending.getChildren().clear();
        rows.getChildren().clear();
        if (history == null) {
            return;
        }
        var data = history.spending();
        var currency = data.mainCurrency();
        Label total = new Label(currency.format(data.totalMinor()));
        total.getStyleClass().add("kpi-value");
        spending.getChildren().add(total);
        if (!data.unconverted().isEmpty()) {
            spending.getChildren().add(line(t("Some spending is missing exchange rates: {0}.",
                    data.unconverted().stream().map(Enum::name).sorted().collect(Collectors.joining(", ")))));
        }
        data.categories().stream().filter(category -> category.spentMinor() > 0)
                .sorted(Comparator.comparingLong(CategorySpending::spentMinor).reversed())
                .forEach(category -> spending.getChildren().add(line(
                        t("{0} · {1}", category.category().name(), currency.format(category.spentMinor())))));
        if (history.rows().isEmpty()) {
            rows.getChildren().add(line(t("No transactions in {0}.", period.getText())));
            return;
        }
        var dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Messages.locale());
        for (TransactionRow row : history.rows()) {
            var date = dateFormat.format(row.source().occurredOn());
            HBox entry = new HBox(12, line(date), line(row.categoryName()), line(row.amount()));
            if (row.note() != null) {
                entry.getChildren().add(line(row.note()));
            }
            entry.getStyleClass().add("tx-row");
            rows.getChildren().add(entry);
        }
    }

    private static Label line(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }
}
