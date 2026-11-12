package com.jeib.ui;

import java.util.Map;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/** Wires main.fxml to {@link MainViewModel}. Holds no state of its own. */
public final class MainController {

    /** Placeholder text for screens that aren't built yet, by roadmap week. */
    private static final Map<String, String> COMING = Map.of(
            "Reports", "Built in Week 6: monthly report and Excel export.",
            "Accounts", "Built in Week 8: multiple accounts and currencies.",
            "Recurring", "Built in Week 8: recurring payments and bill reminders.",
            "Goals", "Built in Week 8: savings goals.",
            "Settings", "Built in Week 10: password lock, backups, theme, updates.");

    private static final String MOON = "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z";
    private static final String SUN = "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM12 2v2M12 20v2M4.93 4.93l1.41 1.41"
            + "M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41";

    private final MainViewModel viewModel;
    private final DialogHost dialogHost;
    private final Dialogs dialogs;
    private final Toasts toasts;
    private final HistoryViewModel history;
    private final QuickAddViewModel quickAddViewModel;
    private final Navigation navigation;

    @FXML private StackPane root;
    @FXML private BorderPane appContent;
    @FXML private StackPane dialogLayer;
    @FXML private StackPane toastLayer;
    @FXML private Button newTransaction;
    @FXML private TextField quickAdd;
    @FXML private HBox quickAddPreviewBar;
    @FXML private Label quickAddPreview;
    @FXML private ToggleGroup navGroup;
    @FXML private Label pageTitle;
    @FXML private Label monthLabel;
    @FXML private Button previousMonth;
    @FXML private Button nextMonth;
    @FXML private VBox transactionsPage;
    @FXML private VBox budgetsPage;
    @FXML private VBox dashboardPage;
    @FXML private Label comingSoon;
    @FXML private Button themeToggle;
    @FXML private SVGPath themeIcon;

    private static final KeyCombination UNDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination REDO = new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination REDO_SHIFT =
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);

    public MainController(MainViewModel viewModel, DialogHost dialogHost, Dialogs dialogs, Toasts toasts,
                          HistoryViewModel history, QuickAddViewModel quickAddViewModel, Navigation navigation) {
        this.viewModel = viewModel;
        this.dialogHost = dialogHost;
        this.dialogs = dialogs;
        this.toasts = toasts;
        this.history = history;
        this.quickAddViewModel = quickAddViewModel;
        this.navigation = navigation;
    }

    @FXML
    private void initialize() {
        dialogHost.attach(dialogLayer, appContent);
        toasts.attach(toastLayer);
        wireQuickAdd();
        // A handler, not an accelerator: a focused text field handles its own Ctrl+Z (undo typing)
        // and consumes it, so the app's undo only runs when the keys weren't used for text.
        root.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (dialogHost.isOpen()) {
                return;
            }
            if (UNDO.match(event)) {
                history.undo();
                event.consume();
            } else if (REDO.match(event) || REDO_SHIFT.match(event)) {
                history.redo();
                event.consume();
            }
        });
        newTransaction.setOnAction(event -> dialogs.openNewTransaction());
        viewModel.setupNeededProperty().addListener((obs, wasNeeded, needed) -> {
            if (needed) {
                dialogs.openSetup(viewModel::setupDone);
            }
        });

        monthLabel.textProperty().bind(viewModel.monthLabel());
        previousMonth.setOnAction(event -> viewModel.previousMonth());
        nextMonth.setOnAction(event -> viewModel.nextMonth());

        // Keep one nav item selected at all times, and swap the page when it changes.
        navGroup.selectedToggleProperty().addListener((obs, previous, current) -> {
            if (current == null) {
                navGroup.selectToggle(previous);
            } else {
                showPage(current);
            }
        });
        showPage(navGroup.getSelectedToggle());
        navigation.attach(page -> navGroup.getToggles().stream()
                .filter(toggle -> page.equals(((ToggleButton) toggle).getText()))
                .findFirst()
                .ifPresent(navGroup::selectToggle));

        themeToggle.setOnAction(event -> viewModel.toggleTheme());
        root.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                applyTheme(scene);
                // Ctrl+N (Cmd+N on macOS): new transaction, from anywhere in the window.
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN),
                        dialogs::openNewTransaction);
            }
        });
        viewModel.themeProperty().addListener((obs, oldTheme, theme) -> {
            if (root.getScene() != null) {
                applyTheme(root.getScene());
            }
        });
    }

    private void wireQuickAdd() {
        quickAdd.textProperty().bindBidirectional(quickAddViewModel.textProperty());
        quickAddPreview.textProperty().bind(quickAddViewModel.previewProperty());
        quickAddPreviewBar.visibleProperty().bind(Bindings.isNotEmpty(quickAddViewModel.previewProperty()));
        quickAddPreviewBar.managedProperty().bind(quickAddPreviewBar.visibleProperty());
        quickAddViewModel.previewIsErrorProperty().addListener((obs, was, isError) -> {
            if (isError) {
                quickAddPreviewBar.getStyleClass().add("quick-add-error");
            } else {
                quickAddPreviewBar.getStyleClass().remove("quick-add-error");
            }
        });
        // Load categories and currency once the database is ready and after setup, then again each
        // time the field is used (cheap, and never stale).
        viewModel.readyProperty().addListener((obs, was, ready) -> {
            if (ready) {
                quickAddViewModel.load();
            }
        });
        viewModel.setupNeededProperty().addListener((obs, was, needed) -> {
            if (!needed) {
                quickAddViewModel.load();
            }
        });
        quickAdd.focusedProperty().addListener((obs, was, focused) -> {
            if (focused) {
                quickAddViewModel.load();
            }
        });
        quickAdd.setOnAction(event -> quickAddViewModel.submit(
                message -> toasts.show(message, "Undo", history::undo)));
        quickAdd.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                quickAddViewModel.clear();
                event.consume();
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
        boolean transactions = "Transactions".equals(page);
        boolean budgets = "Budgets".equals(page);
        dashboardPage.setVisible(dashboard);
        dashboardPage.setManaged(dashboard);
        comingSoon.setText(COMING.getOrDefault(page, ""));
        boolean built = dashboard || transactions || budgets;
        comingSoon.setVisible(!built);
        comingSoon.setManaged(!built);
        transactionsPage.setVisible(transactions);
        transactionsPage.setManaged(transactions);
        budgetsPage.setVisible(budgets);
        budgetsPage.setManaged(budgets);
        navigation.pageShown(page);
    }
}
