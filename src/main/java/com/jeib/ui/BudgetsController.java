package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.BudgetPlan;
import com.jeib.model.LimitStatus;
import com.jeib.model.SupportedCurrency;
import com.jeib.service.BudgetService.LimitLine;
import com.jeib.service.BudgetService.MonthBudget;
import java.util.stream.Collectors;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Wires budgets.fxml to {@link BudgetsViewModel}. */
public final class BudgetsController {

    private final BudgetsViewModel viewModel;
    private final Dialogs dialogs;
    private final BudgetPlansViewModel plans;
    private final Runnable createPlan;

    @FXML private Label intro;
    @FXML private Label warning;
    @FXML private VBox overallCard;
    @FXML private VBox rowsBox;
    @FXML private Label message;
    @FXML private Button newBudgetPlan;
    @FXML private ComboBox<BudgetPlan> budgetPlanPicker;
    @FXML private Button showMonthlyLimits;
    @FXML private Label emptyBudgetPlans;
    @FXML private VBox selectedBudgetPlan;
    @FXML private VBox monthlyLimits;

    public BudgetsController(BudgetsViewModel viewModel, Dialogs dialogs, BudgetPlansViewModel plans, Runnable createPlan) {
        this.viewModel = viewModel;
        this.dialogs = dialogs;
        this.plans = plans;
        this.createPlan = createPlan;
    }

    @FXML
    private void initialize() {
        newBudgetPlan.setOnAction(event -> createPlan.run());
        budgetPlanPicker.setConverter(BudgetPlanLabels.converter());
        budgetPlanPicker.setItems(plans.plans);
        budgetPlanPicker.valueProperty().bindBidirectional(plans.selected);
        budgetPlanPicker.visibleProperty().bind(Bindings.isNotEmpty(plans.plans));
        budgetPlanPicker.managedProperty().bind(budgetPlanPicker.visibleProperty());
        emptyBudgetPlans.visibleProperty().bind(budgetPlanPicker.visibleProperty().not());
        emptyBudgetPlans.managedProperty().bind(emptyBudgetPlans.visibleProperty());
        showMonthlyLimits.setOnAction(event -> plans.selected.set(null));
        showMonthlyLimits.visibleProperty().bind(plans.selected.isNotNull());
        showMonthlyLimits.managedProperty().bind(showMonthlyLimits.visibleProperty());
        monthlyLimits.visibleProperty().bind(plans.selected.isNull());
        monthlyLimits.managedProperty().bind(monthlyLimits.visibleProperty());
        BudgetPlanDetails.bind(selectedBudgetPlan, plans);
        message.textProperty().bind(viewModel.messageProperty());
        message.visibleProperty().bind(Bindings.isNotEmpty(viewModel.messageProperty()));
        message.managedProperty().bind(message.visibleProperty());
        viewModel.budgetProperty().addListener((obs, old, budget) -> render(budget));
        render(viewModel.budgetProperty().get());
    }

    private void render(MonthBudget budget) {
        if (budget == null) {
            return;
        }
        SupportedCurrency main = budget.mainCurrency();
        intro.setText(t("Limits apply to every month and are in {0}.", main));
        if (budget.unconverted().isEmpty()) {
            warning.setVisible(false);
            warning.setManaged(false);
        } else {
            String codes = budget.unconverted().stream().map(Enum::name).collect(Collectors.joining(", "));
            warning.setText(t("Spending in {0} isn't counted yet: it has no exchange rate. "
                    + "Open one of those transactions and enter the rate.", codes));
            warning.setVisible(true);
            warning.setManaged(true);
        }
        renderOverall(budget.overall(), main);
        rowsBox.getChildren().setAll(budget.categories().stream().map(line -> row(line, main)).toList());
    }

    private void renderOverall(LimitLine overall, SupportedCurrency main) {
        Label title = new Label(t("Monthly limit · all spending"));
        title.getStyleClass().add("card-title");
        if (overall.limitMinor() == null) {
            Label text = new Label(t("Spent {0} this month. Set an overall limit "
                    + "to see how much is left and how much you can spend per day.", main.format(overall.spentMinor())));
            text.getStyleClass().add("muted");
            text.setWrapText(true);
            Button set = new Button(t("Set monthly limit"));
            set.setId("setOverallLimit");
            set.getStyleClass().add("button-primary");
            set.setOnAction(event -> dialogs.openLimit(overall, main));
            overallCard.getChildren().setAll(title, text, set);
            return;
        }
        Label amounts = new Label(t("{0} of {1}", main.format(overall.spentMinor()), main.format(overall.limitMinor())));
        amounts.getStyleClass().add("limit-big");
        long remaining = overall.remainingMinor();
        Label left = new Label(remaining >= 0 ? t("{0} left", main.format(remaining)) : t("{0} over", main.format(-remaining)));
        left.getStyleClass().add("muted");
        HBox top = new HBox(amounts, spacer(), percent(overall), StatusTile.of(overall.status()));
        top.getStyleClass().add("limit-top");
        var edit = EditButton.create(() -> dialogs.openLimit(overall, main));
        var heading = new HBox(title, spacer(), edit);
        heading.getStyleClass().add("limit-top");
        overallCard.getChildren().setAll(heading, top, bar(overall), left);
    }

    private Node row(LimitLine line, SupportedCurrency main) {
        Region swatch = new Region();
        swatch.getStyleClass().addAll("swatch", "swatch-" + line.category().colorIndex());
        Label name = new Label(line.category().name());
        name.setWrapText(true);
        HBox category = new HBox(swatch, name);
        category.getStyleClass().add("limit-col-category");

        VBox middle = new VBox();
        middle.getStyleClass().add("limit-col-progress");
        HBox.setHgrow(middle, Priority.ALWAYS);
        Label amounts;
        if (line.limitMinor() == null) {
            amounts = new Label(t("{0} spent", main.format(line.spentMinor())));
        } else {
            amounts = new Label(t("{0} of {1}", main.format(line.spentMinor()), main.format(line.limitMinor())));
            middle.getChildren().add(bar(line));
        }
        amounts.getStyleClass().add("muted");
        middle.getChildren().add(amounts);

        Label pct = line.limitMinor() == null ? new Label("") : percent(line);
        pct.getStyleClass().add("limit-col-percent");
        Node status = StatusTile.of(line.status());
        HBox statusBox = new HBox(status);
        statusBox.getStyleClass().add("limit-col-status");

        Button action;
        if (line.limitMinor() == null) {
            action = new Button(t("Set limit"));
            action.getStyleClass().addAll("button-link", "limit-set-action");
            action.setMinWidth(Region.USE_PREF_SIZE);
            action.setOnAction(event -> dialogs.openLimit(line, main));
        } else {
            action = EditButton.create(() -> dialogs.openLimit(line, main));
        }
        var actionBox = new HBox(action);
        actionBox.getStyleClass().add("limit-action");
        HBox row = new HBox(category, middle, pct, statusBox, actionBox);
        row.getStyleClass().addAll("tx-row", "limit-row");
        return row;
    }

    /** Status colors only for status (color system §1): the bar's color follows OK / near / over. */
    private static ProgressBar bar(LimitLine line) {
        ProgressBar bar = new ProgressBar(Math.min(1.0, line.percent() / 100.0));
        bar.getStyleClass().addAll("limit-bar", switch (line.status()) {
            case EXCEEDED -> "limit-bar-exceeded";
            case WARNING -> "limit-bar-warning";
            default -> "limit-bar-ok";
        });
        bar.setMaxWidth(Double.MAX_VALUE);
        return bar;
    }

    /** The percentage as text is required next to every bar (color system §4: the bar alone isn't enough). */
    private static Label percent(LimitLine line) {
        Label label = new Label(line.percent() + "%");
        label.getStyleClass().add(line.status() == LimitStatus.EXCEEDED ? "percent-exceeded" : "percent");
        return label;
    }

    private static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

}
