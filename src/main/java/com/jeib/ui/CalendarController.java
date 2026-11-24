package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.i18n.Messages;
import com.jeib.model.BudgetPlan;
import com.jeib.model.DateRange;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javafx.beans.InvalidationListener;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class CalendarController {

    private enum View { MONTH, WEEK, YEAR }

    private static final PseudoClass TODAY = PseudoClass.getPseudoClass("today");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected-date");
    private static final LocalDate FIRST_DATE = LocalDate.of(1, 1, 1);
    private static final LocalDate LAST_DATE = LocalDate.of(9999, 12, 31);
    private static final int TODAY_PREVIEW = 4;

    record Boundary(String planName, boolean start) {
        String label() {
            return start ? t("Budget starts · {0}", planName) : t("Budget ends · {0}", planName);
        }
    }

    private record DayCell(Button button, HBox markers, VBox activity, String dateLabel) { }

    private final CalendarViewModel viewModel;
    private final Dialogs dialogs;
    private final BudgetPlansViewModel plans;
    private final DayOfWeek firstWeekday = WeekFields.of(Messages.locale()).getFirstDayOfWeek();
    private final DateTimeFormatter monthName = DateTimeFormatter.ofPattern("LLLL yyyy", Messages.locale());
    private final Map<LocalDate, DayCell> dayCells = new LinkedHashMap<>();
    private Map<LocalDate, List<Boundary>> boundaries = Map.of();
    private final ToggleGroup modes = new ToggleGroup();
    private View view = View.MONTH;
    private LocalDate selectedDate;
    private YearMonth miniMonth;

    @FXML private Label miniTitle;
    @FXML private GridPane miniDays;
    @FXML private Button miniPrevious;
    @FXML private Button miniNext;
    @FXML private Button todayButton;
    @FXML private VBox todayOperations;
    @FXML private Button viewToday;
    @FXML private Label periodTitle;
    @FXML private Button previousPeriod;
    @FXML private Button nextPeriod;
    @FXML private ToggleButton monthView;
    @FXML private ToggleButton weekView;
    @FXML private ToggleButton yearView;
    @FXML private GridPane days;
    @FXML private GridPane months;
    @FXML private HBox budgetLegend;
    @FXML private Label calendarError;

    public CalendarController(CalendarViewModel viewModel, Dialogs dialogs, BudgetPlansViewModel plans) {
        this.viewModel = viewModel;
        this.dialogs = dialogs;
        this.plans = plans;
    }

    @FXML
    private void initialize() {
        selectedDate = viewModel.today.get();
        calendarError.textProperty().bind(viewModel.calendarError);
        calendarError.visibleProperty().bind(calendarError.textProperty().isNotEmpty());
        calendarError.managedProperty().bind(calendarError.visibleProperty());
        equalColumns(miniDays, 7);
        equalColumns(days, 7);
        equalColumns(months, 4);

        wireMode(monthView, View.MONTH);
        wireMode(weekView, View.WEEK);
        wireMode(yearView, View.YEAR);
        monthView.setSelected(true);

        configureArrow(miniPrevious, true, t("Previous month"), () -> showMini(miniMonth.minusMonths(1)));
        configureArrow(miniNext, false, t("Next month"), () -> showMini(miniMonth.plusMonths(1)));
        configureArrow(previousPeriod, true, t("Previous month"), () -> showDate(step(-1)));
        configureArrow(nextPeriod, false, t("Next month"), () -> showDate(step(1)));
        todayButton.setOnAction(event -> showDate(viewModel.today.get()));
        viewToday.setOnAction(event -> {
            if (view == View.YEAR) {
                view = View.MONTH;
                monthView.setSelected(true);
            }
            LocalDate today = viewModel.today.get();
            showDate(today);
            openDay(today, viewToday);
        });

        viewModel.firstMonth.addListener((obs, before, after) -> updateNavigation());
        viewModel.days.addListener((obs, before, after) -> updateDays());
        viewModel.todayRows.addListener((obs, before, after) -> showTodayOperations());
        viewModel.todayError.addListener((obs, before, after) -> showTodayOperations());
        viewModel.today.addListener((obs, before, after) -> {
            showMini(miniMonth);
            updateDays();
        });
        plans.plans.addListener((InvalidationListener) obs -> {
            boundaries = budgetBoundaries(plans.plans);
            updateNavigation();
            updateDays();
        });
        showDate(selectedDate);
        showTodayOperations();
    }

    void refreshToday() {
        viewModel.refreshToday();
    }

    private void wireMode(ToggleButton button, View mode) {
        button.setToggleGroup(modes);
        button.setOnAction(event -> selectMode(mode, button));
    }

    private void selectMode(View mode, ToggleButton button) {
        button.setSelected(true);
        if (view != mode) {
            view = mode;
            showDate(selectedDate);
        }
    }

    private LocalDate step(int direction) {
        return switch (view) {
            case MONTH -> selectedDate.plusMonths(direction);
            case WEEK -> selectedDate.plusWeeks(direction);
            case YEAR -> selectedDate.plusYears(direction);
        };
    }

    private void showDate(LocalDate date) {
        selectedDate = date;
        showMini(YearMonth.from(date));
        dayCells.clear();
        days.getChildren().clear();
        months.getChildren().clear();
        setVisible(days, view != View.YEAR);
        setVisible(months, view == View.YEAR);
        switch (view) {
            case MONTH -> showMonth(YearMonth.from(date));
            case WEEK -> showWeek(date);
            case YEAR -> showYear(date.getYear());
        }
        updateNavigation();
        updateDays();
    }

    private void updateNavigation() {
        int firstYear = viewModel.firstMonth.get() == null
                ? selectedDate.getYear() : viewModel.firstMonth.get().getYear();
        for (LocalDate boundary : boundaries.keySet()) {
            firstYear = Math.min(firstYear, boundary.getYear());
        }
        miniPrevious.setDisable(!miniMonth.isAfter(YearMonth.of(firstYear, 1)));
        miniNext.setDisable(miniMonth.equals(YearMonth.from(LAST_DATE)));
        previousPeriod.setDisable(step(-1).getYear() < firstYear);
        nextPeriod.setDisable(step(1).isAfter(LAST_DATE));
        arrowLabel(previousPeriod, switch (view) {
            case MONTH -> t("Previous month");
            case WEEK -> t("Previous week");
            case YEAR -> t("Previous year");
        });
        arrowLabel(nextPeriod, switch (view) {
            case MONTH -> t("Next month");
            case WEEK -> t("Next week");
            case YEAR -> t("Next year");
        });
    }

    private void showMini(YearMonth month) {
        miniMonth = month;
        miniTitle.setText(monthName.format(month));
        miniDays.getChildren().clear();
        addWeekdays(miniDays, true);
        int offset = weekdayOffset(month.atDay(1));
        int slots = Math.max(35, (offset + month.lengthOfMonth() + 6) / 7 * 7);
        for (int position = 0; position < slots; position++) {
            LocalDate date = month.atDay(1).plusDays(position - offset);
            if (!YearMonth.from(date).equals(month)) {
                var label = new Label(Integer.toString(date.getDayOfMonth()));
                label.setMaxWidth(Double.MAX_VALUE);
                label.getStyleClass().add("calendar-mini-muted");
                miniDays.add(label, position % 7, position / 7 + 1);
                continue;
            }
            var button = new Button(Integer.toString(date.getDayOfMonth()));
            button.setMinWidth(0);
            button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("calendar-mini-day");
            button.setAccessibleText(Messages.date(date, FormatStyle.FULL));
            button.pseudoClassStateChanged(TODAY, date.equals(viewModel.today.get()));
            button.pseudoClassStateChanged(SELECTED, date.equals(selectedDate));
            button.setOnAction(event -> {
                if (view == View.YEAR) {
                    view = View.MONTH;
                    monthView.setSelected(true);
                }
                showDate(date);
                days.requestFocus();
            });
            miniDays.add(button, position % 7, position / 7 + 1);
        }
        updateNavigation();
    }

    private void showMonth(YearMonth month) {
        periodTitle.setText(monthName.format(month));
        DateRange range = DateRange.ofMonth(month);
        viewModel.showRange(range);
        LocalDate gridStart = range.start().minusDays(weekdayOffset(range.start()));
        int length = month.lengthOfMonth() + weekdayOffset(range.start());
        addDayGrid(gridStart, (length + 6) / 7 * 7, range);
    }

    private void showWeek(LocalDate date) {
        DateRange range = weekDates(date, firstWeekday);
        var start = range.start();
        var end = range.end();
        String startText = YearMonth.from(start).equals(YearMonth.from(end))
                ? Integer.toString(start.getDayOfMonth())
                : DateTimeFormatter.ofPattern(start.getYear() == end.getYear() ? "d MMM" : "d MMM yyyy",
                        Messages.locale()).format(start);
        String endText = DateTimeFormatter.ofPattern("d MMM yyyy", Messages.locale()).format(end);
        periodTitle.setText(t("{0} to {1}", startText, endText));
        viewModel.showRange(range);
        addDayGrid(date.with(TemporalAdjusters.previousOrSame(firstWeekday)), 7, range);
    }

    static DateRange weekDates(LocalDate date, DayOfWeek firstWeekday) {
        LocalDate start = date.with(TemporalAdjusters.previousOrSame(firstWeekday));
        LocalDate end = start.plusDays(6);
        return new DateRange(start.isBefore(FIRST_DATE) ? FIRST_DATE : start, end.isAfter(LAST_DATE) ? LAST_DATE : end);
    }

    private void showYear(int year) {
        periodTitle.setText(Integer.toString(year));
        viewModel.showRange(null);
        for (int index = 0; index < 12; index++) {
            YearMonth choice = YearMonth.of(year, index + 1);
            var button = new Button(choice.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, Messages.locale()));
            button.setId("calendar-month-" + choice.getMonthValue());
            button.setAccessibleText(monthName.format(choice));
            button.setMaxWidth(Double.MAX_VALUE);
            button.setMinWidth(0);
            button.getStyleClass().add("calendar-month-choice");
            button.pseudoClassStateChanged(TODAY, choice.equals(YearMonth.from(viewModel.today.get())));
            button.setOnAction(event -> {
                view = View.MONTH;
                monthView.setSelected(true);
                showDate(choice.atDay(1));
                monthView.requestFocus();
            });
            months.add(button, index % 4, index / 4);
        }
    }

    private void addDayGrid(LocalDate start, int length, DateRange range) {
        addWeekdays(days, false);
        for (int index = 0; index < length; index++) {
            LocalDate date = start.plusDays(index);
            Region cell;
            if (date.isBefore(range.start()) || date.isAfter(range.end())) {
                var label = new Label(Integer.toString(date.getDayOfMonth()));
                label.getStyleClass().addAll("calendar-cell", "calendar-outside-day");
                cell = label;
            } else {
                cell = day(date);
            }
            if (view == View.WEEK) {
                cell.getStyleClass().add("calendar-week-cell");
            }
            cell.setMaxWidth(Double.MAX_VALUE);
            days.add(cell, index % 7, index / 7 + 1);
        }
    }

    private Button day(LocalDate date) {
        var number = new Label(Integer.toString(date.getDayOfMonth()));
        number.getStyleClass().add("calendar-day-number");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var markers = new HBox(4);
        markers.setAlignment(Pos.CENTER);
        var heading = new HBox(3, number, spacer, markers);
        heading.setAlignment(Pos.CENTER_LEFT);
        var activity = new VBox(4);
        var graphic = new VBox(7, heading, activity);
        var button = new Button();
        button.setGraphic(graphic);
        button.setId("calendar-day-" + date.getDayOfMonth());
        button.setMinWidth(0);
        button.getStyleClass().addAll("calendar-cell", "calendar-day");
        graphic.prefWidthProperty().bind(javafx.beans.binding.Bindings.max(0, button.widthProperty().subtract(14)));
        button.setOnAction(event -> {
            selectedDate = date;
            showMini(YearMonth.from(date));
            updateDays();
            openDay(date, button);
        });
        dayCells.put(date, new DayCell(button, markers, activity, Messages.date(date, FormatStyle.FULL)));
        return button;
    }

    private void openDay(LocalDate date, Button trigger) {
        dialogs.openCalendarDay(date, viewModel.rowsOn(date), viewModel.calendarError, trigger);
    }

    private void updateDays() {
        var entries = viewModel.days.get();
        boolean showLegend = false;
        for (var item : dayCells.entrySet()) {
            var date = item.getKey();
            var cell = item.getValue();
            var rows = entries == null ? List.<TransactionRow>of() : entries.getOrDefault(date, List.of());
            var dayBoundaries = boundaries.getOrDefault(date, List.of());
            renderActivity(cell, rows, dayBoundaries);
            showLegend |= !dayBoundaries.isEmpty();
            cell.button().pseudoClassStateChanged(TODAY, date.equals(viewModel.today.get()));
            cell.button().pseudoClassStateChanged(SELECTED, date.equals(selectedDate));
        }
        setVisible(budgetLegend, showLegend);
    }

    private void renderActivity(DayCell cell, List<TransactionRow> rows, List<Boundary> dayBoundaries) {
        cell.markers().getChildren().clear();
        boolean starts = dayBoundaries.stream().anyMatch(Boundary::start);
        boolean ends = dayBoundaries.stream().anyMatch(boundary -> !boundary.start());
        if (starts) {
            cell.markers().getChildren().add(marker("budget-start-marker"));
        }
        if (ends) {
            cell.markers().getChildren().add(marker("budget-end-marker"));
        }
        var activity = cell.activity();
        activity.getChildren().clear();
        int preview = Math.min(rows.size(), view == View.WEEK ? 3 : 1);
        for (int index = 0; index < preview; index++) {
            var row = rows.get(index);
            var dot = marker("calendar-day-marker");
            dot.getStyleClass().add("swatch-" + row.colorIndex());
            var name = new Label(row.categoryName());
            name.getStyleClass().add("calendar-activity-name");
            name.setMinWidth(0);
            var line = new HBox(4, dot, name);
            line.setAlignment(Pos.CENTER_LEFT);
            activity.getChildren().add(line);
        }
        if (rows.size() > preview) {
            var more = new Label(t("+{0} more", rows.size() - preview));
            more.getStyleClass().addAll("muted", "calendar-activity-name");
            activity.getChildren().add(more);
        }
        String accessible = rows.isEmpty() ? cell.dateLabel() : t("{0} · Has transactions", cell.dateLabel());
        if (dayBoundaries.isEmpty()) {
            cell.button().setTooltip(null);
        } else {
            String description = dayBoundaries.stream().map(Boundary::label).collect(Collectors.joining("\n"));
            cell.button().setTooltip(new Tooltip(description));
            accessible += "\n" + description;
        }
        cell.button().setAccessibleText(accessible);
    }

    static Map<LocalDate, List<Boundary>> budgetBoundaries(List<BudgetPlan> plans) {
        Map<LocalDate, List<Boundary>> dates = new LinkedHashMap<>();
        for (BudgetPlan plan : plans) {
            dates.computeIfAbsent(plan.dates().start(), date -> new ArrayList<>()).add(new Boundary(plan.name(), true));
            dates.computeIfAbsent(plan.dates().end(), date -> new ArrayList<>()).add(new Boundary(plan.name(), false));
        }
        return dates;
    }

    private void showTodayOperations() {
        var rows = viewModel.todayRows.get();
        var error = viewModel.todayError.get();
        todayOperations.getChildren().clear();
        setVisible(viewToday, rows != null && !rows.isEmpty() && error.isEmpty());
        if (!error.isEmpty() || rows == null || rows.isEmpty()) {
            var message = new Label(!error.isEmpty() ? error
                    : rows == null ? t("Loading transactions…") : t("No operations today."));
            message.setWrapText(true);
            message.getStyleClass().add("muted");
            todayOperations.getChildren().add(message);
            return;
        }
        rows.stream().limit(TODAY_PREVIEW).map(CalendarController::todayOperation)
                .forEach(todayOperations.getChildren()::add);
        if (rows.size() > TODAY_PREVIEW) {
            var more = new Label(t("+{0} more", rows.size() - TODAY_PREVIEW));
            more.getStyleClass().add("muted");
            todayOperations.getChildren().add(more);
        }
    }

    private static VBox todayOperation(TransactionRow row) {
        var dot = marker("swatch");
        dot.getStyleClass().add("swatch-" + row.colorIndex());
        var category = new Label(row.categoryName());
        category.getStyleClass().add("calendar-operation-category");
        HBox.setHgrow(category, Priority.ALWAYS);
        category.setMaxWidth(Double.MAX_VALUE);
        category.setMinWidth(0);
        var amount = new Label(row.amount());
        amount.getStyleClass().add(row.income() ? "amount-income" : "amount");
        amount.setMinWidth(Region.USE_PREF_SIZE);
        var heading = new HBox(5, dot, category, amount);
        heading.setAlignment(Pos.CENTER_LEFT);
        var operation = new VBox(3, heading);
        if (row.note() != null && !row.note().isBlank()) {
            var note = new Label(row.note());
            note.getStyleClass().add("muted");
            note.setMaxWidth(Double.MAX_VALUE);
            operation.getChildren().add(note);
        }
        operation.getStyleClass().add("calendar-operation");
        return operation;
    }

    private void addWeekdays(GridPane grid, boolean mini) {
        for (int column = 0; column < 7; column++) {
            DayOfWeek day = firstWeekday.plus(column);
            var label = new Label(day.getDisplayName(mini ? TextStyle.NARROW_STANDALONE : TextStyle.SHORT_STANDALONE,
                    Messages.locale()));
            label.getStyleClass().add(mini ? "calendar-mini-weekday" : "calendar-weekday");
            label.setMaxWidth(Double.MAX_VALUE);
            label.setAccessibleText(day.getDisplayName(TextStyle.FULL, Messages.locale()));
            grid.add(label, column, 0);
        }
    }

    private int weekdayOffset(LocalDate date) {
        return Math.floorMod(date.getDayOfWeek().getValue() - firstWeekday.getValue(), 7);
    }

    private static Region marker(String style) {
        var region = new Region();
        region.getStyleClass().add(style);
        return region;
    }

    private static void setVisible(Region region, boolean visible) {
        region.setVisible(visible);
        region.setManaged(visible);
    }

    private static void equalColumns(GridPane grid, int count) {
        for (int column = 0; column < count; column++) {
            var width = new ColumnConstraints();
            width.setPercentWidth(100.0 / count);
            grid.getColumnConstraints().add(width);
        }
    }

    private static void configureArrow(Button button, boolean previous, String label, Runnable action) {
        button.setText(previous == Messages.rightToLeft() ? "›" : "‹");
        arrowLabel(button, label);
        button.setOnAction(event -> action.run());
    }

    private static void arrowLabel(Button button, String label) {
        button.setAccessibleText(label);
        if (button.getTooltip() == null) {
            button.setTooltip(new Tooltip(label));
        } else {
            button.getTooltip().setText(label);
        }
    }
}
