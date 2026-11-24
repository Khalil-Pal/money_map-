package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.BudgetPlan;
import com.jeib.model.Category;
import com.jeib.model.SupportedCurrency;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

public final class BudgetPlanDialogController {

    private final BudgetPlanDialogViewModel viewModel;
    private final LocalDate today;
    private final boolean firstPlan;
    private final Runnable close;
    private final Runnable categoryAdded;
    private final Consumer<BudgetPlan> saved;
    private final Map<String, CheckBox> choices = new LinkedHashMap<>();

    @FXML private Label planTitle;
    @FXML private TextField planName;
    @FXML private TextField amount;
    @FXML private ComboBox<SupportedCurrency> planCurrency;
    @FXML private DatePicker startDate;
    @FXML private DatePicker endDate;
    @FXML private FlowPane categoryChoices;
    @FXML private TextField planCategoryName;
    @FXML private Button addPlanCategory;
    @FXML private Label error;
    @FXML private Button savePlan;
    @FXML private Button cancelPlan;

    public BudgetPlanDialogController(BudgetPlanDialogViewModel viewModel, LocalDate today, boolean firstPlan,
                                      Runnable close, Runnable categoryAdded, Consumer<BudgetPlan> saved) {
        this.viewModel = viewModel;
        this.today = today;
        this.firstPlan = firstPlan;
        this.close = close;
        this.categoryAdded = categoryAdded;
        this.saved = saved;
    }

    @FXML
    private void initialize() {
        DateRangeDialogController.localize(startDate);
        DateRangeDialogController.localize(endDate);
        startDate.setValue(today);
        endDate.setValue(today.withDayOfMonth(today.lengthOfMonth()));
        if (firstPlan) {
            planTitle.setText(t("Create your first budget plan"));
            cancelPlan.setCancelButton(false);
            cancelPlan.setVisible(false);
            cancelPlan.setManaged(false);
        }
        savePlan.disableProperty().bind(viewModel.busy().or(planCurrency.valueProperty().isNull()));
        cancelPlan.disableProperty().bind(viewModel.busy());
        addPlanCategory.disableProperty().bind(viewModel.busy().or(planCategoryName.textProperty().isEmpty()));
        error.textProperty().bind(viewModel.error());
        error.managedProperty().bind(error.textProperty().isNotEmpty());
        error.visibleProperty().bind(error.managedProperty());
        viewModel.load(options -> {
            planCurrency.getItems().setAll(SupportedCurrency.values());
            planCurrency.setValue(options.mainCurrency());
            options.categories().forEach(category -> addChoice(category, false));
            planName.requestFocus();
        });
    }

    @FXML
    private void save() {
        Set<String> selected = choices.entrySet().stream().filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        viewModel.save(planName.getText(), startDate.getValue(), endDate.getValue(), amount.getText(),
                planCurrency.getValue(), selected, saved);
    }

    @FXML
    private void addCategory() {
        if (addPlanCategory.isDisabled()) {
            return;
        }
        viewModel.addCategory(planCategoryName.getText(), category -> {
            addChoice(category, true);
            planCategoryName.clear();
            categoryAdded.run();
        });
    }

    private void addChoice(Category category, boolean selected) {
        var choice = new CheckBox(category.name());
        choice.setSelected(selected);
        choices.put(category.id(), choice);
        categoryChoices.getChildren().add(choice);
    }

    @FXML
    private void cancel() {
        close.run();
    }
}
