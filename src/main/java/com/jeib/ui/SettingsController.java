package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.SupportedCurrency;
import java.nio.file.Path;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Wires settings.fxml to {@link SettingsViewModel} (currencies) and {@link MainViewModel} (theme). */
public final class SettingsController {

    private final SettingsViewModel viewModel;
    private final MainViewModel main;
    private final Path dataFile;
    private final LanguageSwitcher languages;
    private final BackupsViewModel backups;
    private final com.jeib.service.CrashLog crashLog;

    @FXML private HBox languageBox;
    @FXML private ToggleButton themeLight;
    @FXML private ToggleButton themeDark;
    @FXML private ToggleButton themeAuto;
    @FXML private HBox mainCurrencyBox;
    @FXML private HBox switchBox;
    @FXML private Label switchText;
    @FXML private Button switchConfirm;
    @FXML private Button switchCancel;
    @FXML private VBox ratesBox;
    @FXML private Button saveRates;
    @FXML private Label error;
    @FXML private Label dataPath;
    @FXML private Button openDataFolder;
    @FXML private VBox backupRows;
    @FXML private HBox restoreBox;
    @FXML private Label restoreText;
    @FXML private Button restoreCancel;
    @FXML private Button restoreConfirm;
    @FXML private Label backupError;
    @FXML private Button backupNow;
    @FXML private Button openBackups;
    @FXML private Button reportProblem;
    @FXML private Button sendFeedback;

    private final ToggleGroup themeGroup = new ToggleGroup();
    private final ToggleGroup currencyGroup = new ToggleGroup();
    private boolean updating; // true while the view sets toggles itself, so listeners don't react

    public SettingsController(SettingsViewModel viewModel, MainViewModel main, Path dataFile,
                              LanguageSwitcher languages, BackupsViewModel backups, com.jeib.service.CrashLog crashLog) {
        this.viewModel = viewModel;
        this.main = main;
        this.dataFile = dataFile;
        this.languages = languages;
        this.backups = backups;
        this.crashLog = crashLog;
    }

    @FXML
    private void initialize() {
        LanguagePicker.fill(languageBox, languages);

        // Appearance
        themeLight.setUserData(ThemeMode.LIGHT);
        themeDark.setUserData(ThemeMode.DARK);
        themeAuto.setUserData(ThemeMode.AUTO);
        for (ToggleButton option : new ToggleButton[] {themeLight, themeDark, themeAuto}) {
            option.setToggleGroup(themeGroup);
            option.setSelected(option.getUserData() == main.themeModeProperty().get());
        }
        themeGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                themeGroup.selectToggle(old);
            } else if (main.themeModeProperty().get() != selected.getUserData()) {
                main.setThemeMode((ThemeMode) selected.getUserData());
            }
        });
        main.themeModeProperty().addListener((obs, old, mode) -> themeGroup.getToggles().stream()
                .filter(toggle -> toggle.getUserData() == mode)
                .findFirst()
                .ifPresent(themeGroup::selectToggle));

        // Main currency: picking one asks to confirm, because limits get converted.
        for (SupportedCurrency currency : SupportedCurrency.values()) {
            ToggleButton option = new ToggleButton(currency.symbol() + "  " + currency.name());
            option.setId("main-" + currency.name());
            option.getStyleClass().add("segment");
            option.setUserData(currency);
            option.setToggleGroup(currencyGroup);
            mainCurrencyBox.getChildren().add(option);
        }
        currencyGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                currencyGroup.selectToggle(old);
            } else if (!updating) {
                viewModel.requestMainCurrency((SupportedCurrency) selected.getUserData());
            }
        });
        viewModel.mainCurrencyProperty().addListener((obs, old, currency) -> {
            selectCurrency(currency);
            renderRates();
        });
        viewModel.pendingMainProperty().addListener((obs, old, pending) -> {
            if (pending == null) {
                selectCurrency(viewModel.mainCurrencyProperty().get());
            } else {
                switchText.setText(t("Switch to {0}? Your limits and exchange rates will be converted "
                        + "to {0}. Past transactions keep their own currency.", pending));
            }
        });
        switchBox.visibleProperty().bind(viewModel.pendingMainProperty().isNotNull());
        switchBox.managedProperty().bind(switchBox.visibleProperty());
        switchConfirm.setOnAction(event -> viewModel.confirmMainCurrency());
        switchConfirm.disableProperty().bind(viewModel.busyProperty());
        switchCancel.setOnAction(event -> viewModel.cancelMainCurrency());

        // Rates
        saveRates.setOnAction(event -> viewModel.saveRates());
        saveRates.disableProperty().bind(viewModel.busyProperty());

        error.textProperty().bind(viewModel.errorProperty());
        error.visibleProperty().bind(Bindings.isNotEmpty(viewModel.errorProperty()));
        error.managedProperty().bind(error.visibleProperty());

        dataPath.setText(dataFile.toString());
        openDataFolder.setOnAction(event -> Links.openFolder(dataFile.getParent()));
        wireBackups();
        reportProblem.setOnAction(event -> Links.browse(
                com.jeib.service.ProblemReport.problemUrl(crashLog.lastLines(40))));
        sendFeedback.setOnAction(event -> Links.browse(com.jeib.service.ProblemReport.feedbackUrl()));
    }

    private void wireBackups() {
        backups.backups().addListener((javafx.collections.ListChangeListener<com.jeib.service.BackupService.Backup>)
                change -> renderBackups());
        renderBackups();
        restoreBox.visibleProperty().bind(backups.pendingRestoreProperty().isNotNull());
        restoreBox.managedProperty().bind(restoreBox.visibleProperty());
        backups.pendingRestoreProperty().addListener((obs, old, backup) -> {
            if (backup != null) {
                restoreText.setText(t("Restore the backup from {0}? Everything you changed after that day will be replaced. "
                        + "Your data right now is saved first, in the backups folder.", longDate(backup.day())));
            }
        });
        restoreCancel.setOnAction(event -> backups.cancelRestore());
        restoreConfirm.setOnAction(event -> backups.confirmRestore());
        restoreConfirm.disableProperty().bind(backups.busyProperty());
        backupNow.setOnAction(event -> backups.backUpNow());
        backupNow.disableProperty().bind(backups.busyProperty());
        openBackups.setOnAction(event -> Links.openFolder(backups.folder()));
        backupError.textProperty().bind(backups.errorProperty());
        backupError.visibleProperty().bind(Bindings.isNotEmpty(backups.errorProperty()));
        backupError.managedProperty().bind(backupError.visibleProperty());
    }

    private void renderBackups() {
        backupRows.getChildren().clear();
        if (backups.backups().isEmpty()) {
            Label none = new Label(t("No backups yet. The first one is made today."));
            none.getStyleClass().add("muted");
            backupRows.getChildren().add(none);
            return;
        }
        for (com.jeib.service.BackupService.Backup backup : backups.backups()) {
            Label day = new Label(longDate(backup.day()));
            Label size = new Label(com.jeib.i18n.Messages.ltr(Math.max(1, backup.sizeBytes() / 1024) + " KB"));
            size.getStyleClass().add("muted");
            javafx.scene.layout.Region gap = new javafx.scene.layout.Region();
            HBox.setHgrow(gap, javafx.scene.layout.Priority.ALWAYS);
            Button restore = new Button(t("Restore"));
            restore.getStyleClass().add("button-link");
            restore.setOnAction(event -> backups.askRestore(backup));
            HBox row = new HBox(12, day, size, gap, restore);
            row.getStyleClass().add("backup-row");
            backupRows.getChildren().add(row);
        }
    }

    private static String longDate(java.time.LocalDate day) {
        return java.time.format.DateTimeFormatter.ofPattern(
                        com.jeib.i18n.Messages.rightToLeft() ? "EEEE، d MMMM yyyy" : "EEEE, d MMMM yyyy",
                        com.jeib.i18n.Messages.locale())
                .format(day);
    }

    private void selectCurrency(SupportedCurrency currency) {
        updating = true;
        currencyGroup.getToggles().stream()
                .filter(toggle -> Objects.equals(toggle.getUserData(), currency))
                .findFirst()
                .ifPresent(currencyGroup::selectToggle);
        updating = false;
    }

    private void renderRates() {
        SupportedCurrency mainCurrency = viewModel.mainCurrencyProperty().get();
        ratesBox.getChildren().clear();
        for (SupportedCurrency currency : viewModel.otherCurrencies()) {
            Label left = new Label(t("1 {0} =", currency));
            left.getStyleClass().add("rate-label");
            TextField field = new TextField();
            field.setId("rate-" + currency.name());
            field.setPromptText(t("not set"));
            field.setPrefWidth(140);
            field.textProperty().bindBidirectional(viewModel.rateText(currency));
            Label right = new Label(mainCurrency.name());
            right.getStyleClass().add("muted");
            HBox row = new HBox(10, left, field, right);
            row.getStyleClass().add("rate-row-line");
            Label rowError = new Label();
            rowError.getStyleClass().add("field-error");
            rowError.textProperty().bind(viewModel.rateError(currency));
            rowError.visibleProperty().bind(Bindings.isNotEmpty(viewModel.rateError(currency)));
            rowError.managedProperty().bind(rowError.visibleProperty());
            ratesBox.getChildren().add(new VBox(4, row, rowError));
        }
    }
}
