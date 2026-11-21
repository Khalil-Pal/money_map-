package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.SupportedCurrency;
import com.jeib.service.PlanningService.GoalsData;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class GoalsController {

    private final PlanningViewModel viewModel;
    @FXML private TextField goalName;
    @FXML private TextField goalTarget;
    @FXML private ComboBox<SupportedCurrency> goalCurrency;
    @FXML private Button addGoal;
    @FXML private Label goalError;
    @FXML private VBox goalRows;

    public GoalsController(PlanningViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        goalCurrency.getItems().setAll(SupportedCurrency.values());
        addGoal.disableProperty().bind(viewModel.goalsBusy.or(viewModel.goals.isNull()));
        RecurringController.bindError(goalError, viewModel.goalsError);
        addGoal.setOnAction(event -> viewModel.createGoal(goalName.getText(), goalTarget.getText(),
                goalCurrency.getValue(), () -> {
                    goalName.clear();
                    goalTarget.clear();
                }));
        viewModel.goals.addListener((obs, before, after) -> render(after));
    }

    private void render(GoalsData data) {
        if (goalCurrency.getValue() == null) {
            goalCurrency.setValue(data.currency());
        }
        goalRows.getChildren().clear();
        if (data.goals().isEmpty()) {
            goalRows.getChildren().add(new Label(t("No savings goals yet.")));
        }
        for (var goal : data.goals()) {
            var title = new Label(goal.name());
            title.getStyleClass().add("card-title");
            var currency = goal.currency();
            var saved = new Label(t("{0} saved of {1}", currency.format(goal.savedMinor()), currency.format(goal.targetMinor())));
            var status = new Label(goal.savedMinor() >= goal.targetMinor() ? t("Goal reached")
                    : t("{0} left", currency.format(goal.targetMinor() - goal.savedMinor())));
            status.getStyleClass().add("muted");
            var amount = new TextField();
            amount.setPromptText(t("Amount"));
            amount.setAccessibleText(t("Savings contribution"));
            var add = new Button(t("Add savings"));
            add.getStyleClass().add("button-primary");
            add.disableProperty().bind(viewModel.goalsBusy.or(amount.textProperty().isEmpty()));
            add.setOnAction(event -> viewModel.addSavings(goal.id(), amount.getText()));
            amount.setOnAction(event -> add.fire());
            var archive = new Button(t("Archive"));
            archive.getStyleClass().add("button-secondary");
            archive.disableProperty().bind(viewModel.goalsBusy);
            archive.setOnAction(event -> RecurringController.confirm(archive, t("Archive this savings goal?"),
                    () -> viewModel.archiveGoal(goal.id())));
            var actions = new HBox(8, amount, add, archive);
            var card = new VBox(title, saved, status, actions);
            card.getStyleClass().addAll("card", "settings-card");
            goalRows.getChildren().add(card);
        }
    }
}
