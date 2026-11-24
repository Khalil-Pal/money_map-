package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.i18n.Language;
import com.jeib.i18n.Messages;
import javafx.animation.KeyFrame;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.geometry.Side;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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
    private final Runnable openFirstPlan;
    private final ThemeTransition themeTransition = new ThemeTransition();

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
    @FXML private HBox transactionActions;
    @FXML private HBox dateRangeControls;
    @FXML private Button chooseDateRange;
    @FXML private Label selectedDateRange;
    @FXML private Button clearDateRange;
    @FXML private VBox transactionsPage;
    @FXML private VBox budgetsPage;
    @FXML private VBox categoriesPage;
    @FXML private VBox settingsPage;
    @FXML private VBox dashboardPage;
    @FXML private VBox calendarPage;
    @FXML private CalendarController calendarPageController;
    @FXML private ScrollPane pageScroll;
    @FXML private VBox recurringPage;
    @FXML private VBox goalsPage;
    @FXML private VBox reportsPage;
    @FXML private ReportsController reportsPageController;
    @FXML private Button settingsButton;
    @FXML private Button themeButton;
    @FXML private SVGPath themeIcon;
    @FXML private Button languageButton;

    private static final KeyCombination UNDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination REDO = new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination REDO_SHIFT =
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);

    public MainController(MainViewModel viewModel, DialogHost dialogHost, Dialogs dialogs, Toasts toasts,
                          HistoryViewModel history, QuickAddViewModel quickAddViewModel, Navigation navigation,
                          LanguageSwitcher languages, Runnable openFirstPlan) {
        this.viewModel = viewModel;
        this.dialogHost = dialogHost;
        this.dialogs = dialogs;
        this.toasts = toasts;
        this.history = history;
        this.quickAddViewModel = quickAddViewModel;
        this.navigation = navigation;
        this.languages = languages;
        this.openFirstPlan = openFirstPlan;
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
                if (viewModel.currencySetupNeeded()) {
                    dialogs.openSetup(openFirstPlan);
                } else {
                    openFirstPlan.run();
                }
            }
        });

        chooseDateRange.setOnAction(event -> dialogs.openDateRange(viewModel.selectedDates(), viewModel::setCustomRange));
        clearDateRange.setOnAction(event -> viewModel.setCustomRange(null));
        selectedDateRange.textProperty().bind(Bindings.createStringBinding(() -> {
            var range = viewModel.customRangeProperty().get();
            return range == null ? "" : Messages.dateRange(range);
        }, viewModel.customRangeProperty()));
        selectedDateRange.visibleProperty().bind(viewModel.customRangeProperty().isNotNull());
        selectedDateRange.managedProperty().bind(selectedDateRange.visibleProperty());
        clearDateRange.visibleProperty().bind(selectedDateRange.visibleProperty());
        clearDateRange.managedProperty().bind(clearDateRange.visibleProperty());

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
                // The transaction shortcut is available on the Transactions page.
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN),
                        () -> {
                            if (transactionsPage.isVisible()) {
                                dialogs.openNewTransaction();
                            }
                        });
            }
        });
        viewModel.themeProperty().addListener((obs, oldTheme, theme) -> {
            if (root.getScene() != null) {
                if (viewModel.readyProperty().get()) {
                    themeTransition.crossFade(root, () -> theme.applyTo(root.getScene()));
                } else {
                    theme.applyTo(root.getScene());
                }
            }
        });
    }

    private void wireQuickAdd() {
        quickAdd.textProperty().bindBidirectional(quickAddViewModel.textProperty());
        quickAddPreview.textProperty().bind(quickAddViewModel.previewProperty());
        quickAddPreviewBar.visibleProperty().bind(transactionActions.visibleProperty()
                .and(Bindings.isNotEmpty(quickAddViewModel.previewProperty())));
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
        var gear = settingsButton.getGraphic();
        var spin = new RotateTransition(Duration.millis(300), gear);
        spin.setInterpolator(Interpolator.SPLINE(0.15, 0.6, 0.3, 1.0));
        settingsButton.setOnAction(event -> {
            double angle = gear.getRotate() % 360;
            spin.stop();
            spin.setFromAngle(angle);
            spin.setToAngle(angle + 60);
            spin.playFromStart();
            navigation.openPage("Settings");
        });

        themeButton.setOnAction(event -> viewModel.toggleTheme());
        showThemeMode(viewModel.themeModeProperty().get());
        viewModel.themeModeProperty().addListener((obs, old, mode) -> showThemeMode(mode));
        // Automatic mode follows the clock while the app is open (dark at 7 PM, light at 7 AM).
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(30), event -> {
            viewModel.refreshTheme();
            if (reportsPage.isVisible()) {
                reportsPageController.refresh();
            }
            if (calendarPage.isVisible()) {
                calendarPageController.refreshToday();
            }
        }));
        clock.setCycleCount(Timeline.INDEFINITE);
        root.sceneProperty().addListener((obs, before, after) -> {
            if (after == null) {
                clock.stop();
            } else {
                clock.play();
            }
        });

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
        boolean calendar = "Calendar".equals(page);
        boolean recurring = "Recurring".equals(page);
        boolean goals = "Goals".equals(page);
        boolean reports = "Reports".equals(page);
        recurringPage.setVisible(recurring);
        recurringPage.setManaged(recurring);
        goalsPage.setVisible(goals);
        goalsPage.setManaged(goals);
        dateRangeControls.setVisible(transactions);
        dateRangeControls.setManaged(transactions);
        transactionActions.setVisible(transactions);
        transactionActions.setManaged(transactions);
        calendarPage.setVisible(calendar);
        calendarPage.setManaged(calendar);
        if (calendar) {
            calendarPageController.refreshToday();
        }
        dashboardPage.setVisible(dashboard);
        dashboardPage.setManaged(dashboard);
        reportsPage.setVisible(reports);
        reportsPage.setManaged(reports);
        if (reports) {
            reportsPageController.refresh();
        }
        transactionsPage.setVisible(transactions);
        transactionsPage.setManaged(transactions);
        budgetsPage.setVisible(budgets);
        budgetsPage.setManaged(budgets);
        categoriesPage.setVisible(categories);
        categoriesPage.setManaged(categories);
        settingsPage.setVisible(settings);
        settingsPage.setManaged(settings);
        if (calendar || reports) {
            pageScroll.setVvalue(pageScroll.getVmin());
        }
        navigation.pageShown(page);
    }
}
