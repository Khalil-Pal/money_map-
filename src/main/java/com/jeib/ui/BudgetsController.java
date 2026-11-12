package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.LimitStatus;
import com.jeib.model.SupportedCurrency;
import com.jeib.service.BudgetService.LimitLine;
import com.jeib.service.BudgetService.MonthBudget;
import java.util.stream.Collectors;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Wires budgets.fxml to {@link BudgetsViewModel}. Every limit row and the overall card open the limit dialog. */
public final class BudgetsController {

    private final BudgetsViewModel viewModel;
    private final Dialogs dialogs;

    @FXML private Label intro;
    @FXML private Label warning;
    @FXML private VBox overallCard;
    @FXML private VBox rowsBox;
    @FXML private Label message;

    public BudgetsController(BudgetsViewModel viewModel, Dialogs dialogs) {
        this.viewModel = viewModel;
        this.dialogs = dialogs;
    }

    @FXML
    private void initialize() {
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
        intro.setText(t("Limits apply to every month and are in {0}. Click one to change it.", main));
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
        overallCard.setOnMouseClicked(null);
        overallCard.setOnKeyPressed(null);
        overallCard.setFocusTraversable(false);
        overallCard.getStyleClass().remove("clickable-card");
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
        overallCard.getChildren().setAll(title, top, bar(overall), left);
        makeClickable(overallCard, () -> dialogs.openLimit(overall, main));
        overallCard.getStyleClass().add("clickable-card");
    }

    private Node row(LimitLine line, SupportedCurrency main) {
        Region swatch = new Region();
        swatch.getStyleClass().addAll("swatch", "swatch-" + line.category().colorIndex());
        Label name = new Label(line.category().name());
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

        HBox row = new HBox(category, middle, pct, statusBox);
        row.getStyleClass().addAll("tx-row", "tx-row-clickable", "limit-row");
        makeClickable(row, () -> dialogs.openLimit(line, main));
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

    private static void makeClickable(Region node, Runnable action) {
        node.setFocusTraversable(true);
        node.setOnMouseClicked(event -> action.run());
        node.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                action.run();
                event.consume();
            }
        });
    }
}
