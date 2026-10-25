package com.jeib.ui;

import com.jeib.model.Category;
import com.jeib.model.TransactionType;
import java.util.Map;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/** Wires main.fxml to {@link MainViewModel}. Holds no state of its own. */
public final class MainController {

    /** Placeholder text for screens that aren't built yet, by roadmap week. */
    private static final Map<String, String> COMING = Map.of(
            "Transactions", "Built in Weeks 3-4: add, edit, delete, monthly list, undo/redo.",
            "Budgets", "Built in Week 5: monthly limits per category and overall.",
            "Reports", "Built in Week 6: monthly report and Excel export.",
            "Accounts", "Built in Week 8: multiple accounts and currencies.",
            "Recurring", "Built in Week 8: recurring payments and bill reminders.",
            "Goals", "Built in Week 8: savings goals.",
            "Settings", "Built in Week 10: password lock, backups, theme, updates.");

    private static final String MOON = "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z";
    private static final String SUN = "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM12 2v2M12 20v2M4.93 4.93l1.41 1.41"
            + "M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41";

    private final MainViewModel viewModel;

    @FXML private BorderPane root;
    @FXML private ToggleGroup navGroup;
    @FXML private Label pageTitle;
    @FXML private Label pageSubtitle;
    @FXML private VBox dashboardCards;
    @FXML private Label comingSoon;
    @FXML private Label databaseStatus;
    @FXML private FlowPane expenseChips;
    @FXML private FlowPane incomeChips;
    @FXML private Button themeToggle;
    @FXML private SVGPath themeIcon;

    public MainController(MainViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        databaseStatus.textProperty().bind(viewModel.statusProperty());

        viewModel.categories().addListener((ListChangeListener<Category>) change -> renderCategories());
        renderCategories();

        // Keep one nav item selected at all times, and swap the page when it changes.
        navGroup.selectedToggleProperty().addListener((obs, previous, current) -> {
            if (current == null) {
                navGroup.selectToggle(previous);
            } else {
                showPage(current);
            }
        });
        showPage(navGroup.getSelectedToggle());

        themeToggle.setOnAction(event -> viewModel.toggleTheme());
        root.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                applyTheme(scene);
            }
        });
        viewModel.themeProperty().addListener((obs, oldTheme, theme) -> {
            if (root.getScene() != null) {
                applyTheme(root.getScene());
            }
        });
    }

    private void applyTheme(Scene scene) {
        Theme theme = viewModel.themeProperty().get();
        theme.applyTo(scene);
        themeToggle.setText(theme == Theme.LIGHT ? "Dark mode" : "Light mode");
        themeIcon.setContent(theme == Theme.LIGHT ? MOON : SUN);
    }

    private void showPage(Toggle toggle) {
        String page = ((ToggleButton) toggle).getText();
        pageTitle.setText(page);
        boolean dashboard = "Dashboard".equals(page);
        pageSubtitle.setText(dashboard ? "The app skeleton: theme, navigation, and the local database." : "");
        dashboardCards.setVisible(dashboard);
        dashboardCards.setManaged(dashboard);
        comingSoon.setText(COMING.getOrDefault(page, ""));
        comingSoon.setVisible(!dashboard);
        comingSoon.setManaged(!dashboard);
    }

    private void renderCategories() {
        expenseChips.getChildren().setAll(viewModel.categories().stream()
                .filter(c -> c.kind() == TransactionType.EXPENSE).map(MainController::chip).toList());
        incomeChips.getChildren().setAll(viewModel.categories().stream()
                .filter(c -> c.kind() == TransactionType.INCOME).map(MainController::chip).toList());
    }

    private static Node chip(Category category) {
        Region swatch = new Region();
        swatch.getStyleClass().addAll("swatch", "swatch-" + category.colorIndex());
        Label name = new Label(category.name());
        HBox chip = new HBox(swatch, name);
        chip.getStyleClass().add("chip");
        if (category.builtIn()) {
            Label builtIn = new Label("built-in");
            builtIn.getStyleClass().add("chip-note");
            chip.getChildren().add(builtIn);
        }
        return chip;
    }
}
