package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import java.util.stream.Collectors;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** The same plan totals and breakdown are shown in Budgets and Dashboard. */
final class BudgetPlanDetails {

    private BudgetPlanDetails() {
    }

    static void bind(VBox card, BudgetPlansViewModel plans) {
        card.visibleProperty().bind(plans.selected.isNotNull());
        card.managedProperty().bind(card.visibleProperty());
        plans.summary.addListener((obs, before, after) -> render(card, plans));
        plans.error.addListener((obs, before, after) -> render(card, plans));
        render(card, plans);
    }

    private static void render(VBox card, BudgetPlansViewModel plans) {
        card.getChildren().clear();
        if (!plans.error.get().isBlank()) {
            card.getChildren().add(label(plans.error.get(), "warning-note"));
            return;
        }
        var summary = plans.summary.get();
        if (summary == null) {
            return;
        }

        var plan = summary.plan();
        var currency = plan.currency();
        long remaining = summary.remainingMinor();
        var status = new HBox(12, StatusTile.of(summary.status()), label(t("{0}% used", summary.percentUsed()), "muted"));
        card.getChildren().addAll(label(BudgetPlanLabels.label(plan), "card-title"),
                label(t("{0} of {1}", currency.format(summary.spentMinor()), currency.format(plan.limitMinor())), "muted"),
                status, label(t("Remaining"), "field-label"),
                label(remaining >= 0 ? currency.format(remaining) : t("Over by {0}", currency.format(-remaining)), "muted"));

        if (!summary.missingRates().isEmpty()) {
            String codes = summary.missingRates().stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
            card.getChildren().add(label(t("Some spending is missing exchange rates: {0}.", codes), "warning-note"));
        } else if (summary.leftPerDay() != null) {
            card.getChildren().addAll(label(t("Left per day"), "field-label"),
                    label(t("{0} / day", currency.format(summary.leftPerDay().perDayMinor())), "muted"));
        }

        card.getChildren().add(label(t("Where it went"), "field-label"));
        for (var entry : summary.byCategory()) {
            card.getChildren().add(label(t("{0} · {1}", entry.category().name(),
                    currency.format(entry.spentMinor())), "muted"));
        }
    }

    private static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        return label;
    }
}
