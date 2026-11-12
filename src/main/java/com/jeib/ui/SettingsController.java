package com.jeib.ui;

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

    @FXML private ToggleButton themeLight;
    @FXML private ToggleButton themeDark;
    @FXML private HBox mainCurrencyBox;
    @FXML private HBox switchBox;
    @FXML private Label switchText;
    @FXML private Button switchConfirm;
    @FXML private Button switchCancel;
    @FXML private VBox ratesBox;
    @FXML private Button saveRates;
    @FXML private Label error;
    @FXML private Label dataPath;

    private final ToggleGroup themeGroup = new ToggleGroup();
    private final ToggleGroup currencyGroup = new ToggleGroup();
    private boolean updating; // true while the view sets toggles itself, so listeners don't react

    public SettingsController(SettingsViewModel viewModel, MainViewModel main, Path dataFile) {
        this.viewModel = viewModel;
        this.main = main;
        this.dataFile = dataFile;
    }

    @FXML
    private void initialize() {
        // Appearance
        themeLight.setUserData(Theme.LIGHT);
        themeDark.setUserData(Theme.DARK);
        themeLight.setToggleGroup(themeGroup);
        themeDark.setToggleGroup(themeGroup);
        themeGroup.selectToggle(main.themeProperty().get() == Theme.DARK ? themeDark : themeLight);
        themeGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                themeGroup.selectToggle(old);
            } else if (main.themeProperty().get() != selected.getUserData()) {
                main.setTheme((Theme) selected.getUserData());
            }
        });
        main.themeProperty().addListener((obs, old, theme) ->
                themeGroup.selectToggle(theme == Theme.DARK ? themeDark : themeLight));

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
                switchText.setText("Switch to " + pending + "? Your limits and exchange rates will be converted "
                        + "to " + pending + ". Past transactions keep their own currency.");
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
            Label left = new Label("1 " + currency + " =");
            left.getStyleClass().add("rate-label");
            TextField field = new TextField();
            field.setId("rate-" + currency.name());
            field.setPromptText("not set");
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
