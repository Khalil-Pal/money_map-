package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.BudgetPlan;
import com.jeib.service.BudgetPlanService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

public final class BudgetPlansViewModel {

    final ObservableList<BudgetPlan> plans = FXCollections.observableArrayList();
    final ObjectProperty<BudgetPlan> selected = new SimpleObjectProperty<>();
    final ObjectProperty<BudgetPlanService.Summary> summary = new SimpleObjectProperty<>();
    final ObjectProperty<String> error = new SimpleObjectProperty<>("");
    private final BudgetPlanService service;
    private final Clock clock;
    private Task<List<BudgetPlan>> pendingList;
    private Task<BudgetPlanService.Summary> pending;

    public BudgetPlansViewModel(BudgetPlanService service, Clock clock) {
        this.service = service;
        this.clock = clock;
        selected.addListener((obs, before, after) -> {
            summary.set(null);
            loadSummary();
        });
    }

    public void reload() {
        if (pendingList != null) {
            pendingList.cancel();
        }
        Task<List<BudgetPlan>> task = new Task<>() {
            @Override
            protected List<BudgetPlan> call() throws Exception {
                return service.list();
            }
        };
        task.setOnSucceeded(event -> {
            if (!plans.equals(task.getValue())) {
                BudgetPlan selection = selected.get();
                plans.setAll(task.getValue());
                selected.set(selection != null && plans.contains(selection) ? selection : null);
            }
            loadSummary();
        });
        task.setOnFailed(event -> error.set(t("Something went wrong: {0}", task.getException().getMessage())));
        pendingList = task;
        Thread.ofVirtual().start(task);
    }

    public void selectCreatedPlan(BudgetPlan plan) {
        selected.set(plan);
    }

    private void loadSummary() {
        BudgetPlan plan = selected.get();
        if (pending != null) {
            pending.cancel();
        }
        error.set("");
        if (plan == null) {
            return;
        }
        var task = new Task<BudgetPlanService.Summary>() {
            @Override
            protected BudgetPlanService.Summary call() throws Exception {
                return service.summarize(plan, LocalDate.now(clock));
            }
        };
        pending = task;
        task.setOnSucceeded(event -> summary.set(task.getValue()));
        task.setOnFailed(event -> error.set(t("Something went wrong: {0}", task.getException().getMessage())));
        Thread.ofVirtual().start(task);
    }
}
