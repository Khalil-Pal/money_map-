package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.i18n.Language;
import com.jeib.i18n.Messages;
import java.util.Map;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Side;
import javafx.scene.Group;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
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
            "Goals", "Built in Week 8: savings goals.");

    private static final String MOON = "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z";
    /** Lucide "sun-moon": the Automatic mode. */
    private static final String SUN_MOON = "M12 8a2.83 2.83 0 0 0 4 4 4 4 0 1 1-4-4M12 2v2M12 20v2M4.9 4.9l1.4 1.4"
            + "M17.7 17.7l1.4 1.4M2 12h2M20 12h2M6.3 17.7l-1.4 1.4M19.1 4.9l-1.4 1.4";
    private static final String SUN = "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM12 2v2M12 20v2M4.93 4.93l1.41 1.41"
            + "M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41";

    private final MainViewModel viewModel;
    private final DialogHost dialogHost;
    private final Dialogs dialogs;
    private final Toasts toasts;
    private final HistoryViewModel history;
    private final QuickAddViewModel quickAddViewModel;
    private final Navigation navigation;
    private final LanguageSwitcher languages;

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
    @FXML private VBox categoriesPage;
    @FXML private VBox settingsPage;
    @FXML private VBox dashboardPage;
    @FXML private Label comingSoon;
    @FXML private Button settingsButton;
    @FXML private Button themeButton;
    @FXML private Group themeIconGroup;
    @FXML private SVGPath themeIcon;
    @FXML private Button languageButton;

    private static final KeyCombination UNDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination REDO = new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination REDO_SHIFT =
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);

    public MainController(MainViewModel viewModel, DialogHost dialogHost, Dialogs dialogs, Toasts toasts,
                          HistoryViewModel history, QuickAddViewModel quickAddViewModel, Navigation navigation,
                          LanguageSwitcher languages) {
        this.viewModel = viewModel;
        this.dialogHost = dialogHost;
        this.dialogs = dialogs;
        this.toasts = toasts;
        this.history = history;
        this.quickAddViewModel = quickAddViewModel;
        this.navigation = navigation;
        this.languages = languages;
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
                .filter(toggle -> page.equals(toggle.getUserData()))
                .findFirst()
                .ifPresent(navGroup::selectToggle));

        wireTopRight();
        root.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                viewModel.themeProperty().get().applyTo(scene); // first time: no animation
                // Ctrl+N (Cmd+N on macOS): new transaction, from anywhere in the window.
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN),
                        dialogs::openNewTransaction);
            }
        });
        viewModel.themeProperty().addListener((obs, oldTheme, theme) -> {
            if (root.getScene() != null) {
                // The new theme spreads out as a circle from the theme button.
                ThemeTransition.reveal(root, themeButton, () -> theme.applyTo(root.getScene()));
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
                message -> toasts.show(message, t("Undo"), history::undo)));
        quickAdd.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                quickAddViewModel.clear();
                event.consume();
            }
        });
    }

    /** Settings shortcut, the theme button (Light / Dark / Automatic), and the language pill. */
    private void wireTopRight() {
        settingsButton.setTooltip(new Tooltip(t("Settings")));
        settingsButton.setOnAction(event -> navigation.openPage("Settings"));

        themeButton.setOnAction(event -> viewModel.toggleTheme());
        showThemeMode(viewModel.themeModeProperty().get());
        viewModel.themeModeProperty().addListener((obs, old, mode) -> {
            showThemeMode(mode);
            ThemeTransition.spin(themeIconGroup);
        });
        // Automatic mode follows the clock while the app is open (dark at 7 PM, light at 7 AM).
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(30), event -> viewModel.refreshTheme()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();

        languageButton.setText(Messages.language().nativeName());
        languageButton.setTooltip(new Tooltip(t("Language")));
        ContextMenu menu = new ContextMenu();
        for (Language language : Language.values()) {
            CheckMenuItem item = new CheckMenuItem(language.nativeName());
            item.setSelected(language == Messages.language());
            item.setOnAction(event -> languages.switchTo(language));
            menu.getItems().add(item);
        }
        languageButton.setOnAction(event -> menu.show(languageButton, Side.BOTTOM, 0, 6));
    }

    private void showThemeMode(ThemeMode mode) {
        String name = switch (mode) {
            case LIGHT -> t("Light mode");
            case DARK -> t("Dark mode");
            case AUTO -> t("Automatic: dark from 7 PM to 7 AM");
        };
        themeIcon.setContent(switch (mode) {
            case LIGHT -> SUN;
            case DARK -> MOON;
            case AUTO -> SUN_MOON;
        });
        themeButton.setTooltip(new Tooltip(name + " · " + t("Click to switch")));
        themeButton.setAccessibleText(name);
    }

    private void showPage(Toggle toggle) {
        // userData is the page's fixed id ("Budgets"); the button text is translated.
        String page = (String) toggle.getUserData();
        pageTitle.setText(t(page));
        boolean dashboard = "Dashboard".equals(page);
        boolean transactions = "Transactions".equals(page);
        boolean budgets = "Budgets".equals(page);
        boolean categories = "Categories".equals(page);
        boolean settings = "Settings".equals(page);
        dashboardPage.setVisible(dashboard);
        dashboardPage.setManaged(dashboard);
        comingSoon.setText(t(COMING.getOrDefault(page, "")));
        boolean built = dashboard || transactions || budgets || categories || settings;
        comingSoon.setVisible(!built);
        comingSoon.setManaged(!built);
        transactionsPage.setVisible(transactions);
        transactionsPage.setManaged(transactions);
        budgetsPage.setVisible(budgets);
        budgetsPage.setManaged(budgets);
        categoriesPage.setVisible(categories);
        categoriesPage.setManaged(categories);
        settingsPage.setVisible(settings);
        settingsPage.setManaged(settings);
        navigation.pageShown(page);
    }
}
