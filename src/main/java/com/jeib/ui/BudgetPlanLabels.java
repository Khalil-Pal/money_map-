package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.i18n.Messages;
import com.jeib.model.BudgetPlan;
import javafx.util.StringConverter;

final class BudgetPlanLabels {
    private BudgetPlanLabels() { }

    static String label(BudgetPlan plan) {
        return t("{0} · {1}", plan.name(), Messages.dateRange(plan.dates()));
    }

    static StringConverter<BudgetPlan> converter() {
        return new StringConverter<>() {
            @Override
            public String toString(BudgetPlan plan) {
                return plan == null ? "" : label(plan);
            }

            @Override
            public BudgetPlan fromString(String text) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
