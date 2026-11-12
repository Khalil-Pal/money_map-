package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.LeftPerDay;
import com.jeib.model.LimitStatus;
import com.jeib.model.SupportedCurrency;
import com.jeib.service.BudgetService.LimitLine;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;

/**
 * Wires dashboard.fxml to {@link DashboardViewModel}. Every card, legend row, and limit row is a
 * drill-down (CLAUDE.md: hover only on clickable things, and these all are).
 */
public final class DashboardController {

    /** "November 2026", in the app's language. */
    private static DateTimeFormatter month() {
        return DateTimeFormatter.ofPattern("MMMM yyyy", com.jeib.i18n.Messages.locale());
    }
    private static final double DONUT_RADIUS = 76;

    private final DashboardViewModel viewModel;
    private final Navigation navigation;
    private final Dialogs dialogs;

    @FXML private Label warning;
    @FXML private Label message;
    @FXML private HBox cards;
    @FXML private VBox chartCard;
    @FXML private VBox limitsCard;

    public DashboardController(DashboardViewModel viewModel, Navigation navigation, Dialogs dialogs) {
        this.viewModel = viewModel;
        this.navigation = navigation;
        this.dialogs = dialogs;
    }

    @FXML
    private void initialize() {
        message.textProperty().bind(viewModel.messageProperty());
        message.visibleProperty().bind(Bindings.isNotEmpty(viewModel.messageProperty()));
        message.managedProperty().bind(message.visibleProperty());
        viewModel.dataProperty().addListener((obs, old, data) -> render(data));
        render(viewModel.dataProperty().get());
    }

    private void render(DashboardData data) {
        if (data == null) {
            return;
        }
        SupportedCurrency main = data.mainCurrency();
        if (data.unconverted().isEmpty()) {
            warning.setVisible(false);
            warning.setManaged(false);
        } else {
            warning.setText(t("Spending in {0} isn't counted yet: it has no exchange rate. "
                    + "Open one of those transactions and enter the rate.",
                    data.unconverted().stream().map(Enum::name).collect(Collectors.joining(", "))));
            warning.setVisible(true);
            warning.setManaged(true);
        }
        cards.getChildren().setAll(spentCard(data, main), remainingCard(data, main), perDayCard(data, main));
        renderChart(data, main);
        renderLimits(data, main);
    }

    // ---- the three number cards --------------------------------------------

    private Node spentCard(DashboardData data, SupportedCurrency main) {
        LimitLine overall = data.overall();
        String sub = overall.limitMinor() == null ? t("No monthly limit set")
                : t("of {0} monthly limit", main.format(overall.limitMinor()));
        return kpi(t("Spent this month"), main.format(overall.spentMinor()), false, muted(sub),
                "spentCard", () -> navigation.openTransactions(null));
    }

    private Node remainingCard(DashboardData data, SupportedCurrency main) {
        LimitLine overall = data.overall();
        if (overall.limitMinor() == null) {
            return kpi(t("Remaining"), "—", false, muted(t("Set a monthly limit to see what's left")),
                    "remainingCard", () -> dialogs.openLimit(overall, main));
        }
        long remaining = overall.remainingMinor();
        String value = remaining >= 0 ? main.format(remaining) : t("Over by {0}", main.format(-remaining));
        HBox sub = new HBox(10, StatusTile.of(overall.status()), muted(t("{0}% used", overall.percent())));
        sub.getStyleClass().add("kpi-sub");
        return kpi(t("Remaining"), value, remaining < 0, sub, "remainingCard", navigation::openBudgets);
    }

    private Node perDayCard(DashboardData data, SupportedCurrency main) {
        LimitLine overall = data.overall();
        if (data.monthOver()) {
            return kpi(t("Left per day"), "—", false, muted(t("{0} is over.", month().format(data.month()))),
                    "perDayCard", navigation::openBudgets);
        }
        if (overall.limitMinor() == null) {
            Button set = new Button(t("Set monthly limit"));
            set.getStyleClass().add("button-primary");
            set.setOnAction(event -> dialogs.openLimit(overall, main));
            VBox sub = new VBox(10, muted(t("Your daily budget comes from your monthly limit.")), set);
            return kpi(t("Left per day"), "—", false, sub, "perDayCard", () -> dialogs.openLimit(overall, main));
        }
        LeftPerDay perDay = data.leftPerDay();
        String days = perDay.daysLeft() == 1 ? t("1 day left") : t("{0} days left", perDay.daysLeft());
        Label sub = perDay.perDayMinor() == 0
                ? styled(t("You've used your whole monthly limit."), "kpi-danger-note")
                : muted(t("{0} · based on your monthly limit", days));
        return kpi(t("Left per day"), t("{0} / day", main.format(perDay.perDayMinor())), perDay.perDayMinor() == 0, sub,
                "perDayCard", navigation::openBudgets);
    }

    private Node kpi(String caption, String value, boolean danger, Node sub, String id, Runnable onClick) {
        Label captionLabel = styled(caption, "kpi-caption");
        Label valueLabel = styled(value, danger ? "kpi-value-danger" : "kpi-value");
        VBox card = new VBox(captionLabel, valueLabel, sub);
        card.setId(id);
        card.getStyleClass().addAll("card", "kpi-card", "clickable-card");
        card.setPrefWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        clickable(card, onClick);
        return card;
    }

    // ---- where the money went ----------------------------------------------

    private void renderChart(DashboardData data, SupportedCurrency main) {
        Label title = styled(t("Where it went"), "card-title");
        if (data.slices().isEmpty()) {
            chartCard.getChildren().setAll(title, muted(t("No spending in {0} yet.", month().format(data.month()))));
            return;
        }
        VBox legend = new VBox(2);
        legend.getStyleClass().add("legend");
        HBox.setHgrow(legend, Priority.ALWAYS);
        for (DashboardData.Slice slice : data.slices()) {
            Region swatch = new Region();
            swatch.getStyleClass().addAll("swatch", "swatch-" + slice.category().colorIndex());
            Label name = new Label(slice.category().name());
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            Label amount = new Label(main.format(slice.amountMinor()));
            // A small but real amount shouldn't read as "0%".
            Label percent = styled(com.jeib.i18n.Messages.ltr(slice.percent() == 0 ? "<1%" : slice.percent() + "%"), "legend-percent");
            HBox row = new HBox(8, swatch, name, gap, amount, percent);
            row.getStyleClass().add("legend-row");
            clickable(row, () -> navigation.openTransactions(slice.category()));
            legend.getChildren().add(row);
        }
        HBox body = new HBox(28, donut(data, main), legend);
        body.getStyleClass().add("chart-body");
        chartCard.getChildren().setAll(title, body);
    }

    /** A donut drawn with arcs (colors from the category palette tokens), the total in the middle. */
    private static Node donut(DashboardData data, SupportedCurrency main) {
        Circle track = new Circle(DONUT_RADIUS);
        track.getStyleClass().add("donut-track");
        Group rings = new Group(track);
        long total = data.overall().spentMinor();
        double start = 90; // 12 o'clock; JavaFX angles go counterclockwise, so lengths are negative
        boolean gaps = data.slices().size() > 1;
        for (DashboardData.Slice slice : data.slices()) {
            double length = 360.0 * slice.amountMinor() / total;
            double drawn = gaps && length > 3 ? length - 1.5 : length;
            Arc arc = new Arc(0, 0, DONUT_RADIUS, DONUT_RADIUS, start, -drawn);
            arc.setType(ArcType.OPEN);
            arc.getStyleClass().addAll("donut-slice", "slice-" + slice.category().colorIndex());
            rings.getChildren().add(arc);
            start -= length;
        }
        Label totalLabel = styled(main.format(total), "donut-total");
        Label caption = styled(t("spent"), "muted");
        VBox center = new VBox(totalLabel, caption);
        center.getStyleClass().add("donut-center");
        StackPane donut = new StackPane(rings, center);
        donut.getStyleClass().add("donut");
        return donut;
    }

    // ---- limits ------------------------------------------------------------

    private void renderLimits(DashboardData data, SupportedCurrency main) {
        Button all = new Button(t("All limits"));
        all.getStyleClass().add("button-link");
        all.setOnAction(event -> navigation.openBudgets());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox header = new HBox(styled(t("Limits"), "card-title"), gap, all);
        header.getStyleClass().add("card-header");
        if (data.limits().isEmpty()) {
            Button set = new Button(t("Set limits"));
            set.getStyleClass().add("button-secondary");
            set.setOnAction(event -> navigation.openBudgets());
            limitsCard.getChildren().setAll(header,
                    muted(t("No category limits yet. Give a category a monthly limit to see how you're doing.")), set);
            return;
        }
        VBox rows = new VBox(4);
        for (LimitLine line : data.limits()) {
            Region swatch = new Region();
            swatch.getStyleClass().addAll("swatch", "swatch-" + line.category().colorIndex());
            Region gapTop = new Region();
            HBox.setHgrow(gapTop, Priority.ALWAYS);
            HBox top = new HBox(8, swatch, new Label(line.category().name()), gapTop,
                    muted(t("{0} of {1}", main.format(line.spentMinor()), main.format(line.limitMinor()))));
            top.getStyleClass().add("limit-mini-top");

            ProgressBar bar = new ProgressBar(Math.min(1.0, line.percent() / 100.0));
            bar.getStyleClass().addAll("limit-bar", switch (line.status()) {
                case EXCEEDED -> "limit-bar-exceeded";
                case WARNING -> "limit-bar-warning";
                default -> "limit-bar-ok";
            });
            bar.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(bar, Priority.ALWAYS);
            Label percent = styled(line.percent() + "%",
                    line.status() == LimitStatus.EXCEEDED ? "percent-exceeded" : "percent");
            HBox bottom = new HBox(12, bar, percent, StatusTile.of(line.status()));
            bottom.getStyleClass().add("limit-mini-bottom");

            VBox row = new VBox(6, top, bottom);
            row.getStyleClass().add("legend-row");
            clickable(row, () -> navigation.openTransactions(line.category()));
            rows.getChildren().add(row);
        }
        limitsCard.getChildren().setAll(header, rows);
    }

    // ---- helpers -----------------------------------------------------------

    private static Label muted(String text) {
        Label label = styled(text, "muted");
        label.setWrapText(true);
        return label;
    }

    private static Label styled(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static void clickable(Region node, Runnable action) {
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
