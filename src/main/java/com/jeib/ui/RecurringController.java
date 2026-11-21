package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.i18n.Messages;
import com.jeib.model.Category;
import com.jeib.model.SupportedCurrency;
import com.jeib.service.PlanningService.RecurringData;
import java.time.format.DateTimeFormatter;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public final class RecurringController {

    private final PlanningViewModel viewModel;
    @FXML private TextField paymentName;
    @FXML private TextField paymentAmount;
    @FXML private TextField paymentDay;
    @FXML private ComboBox<Category> paymentCategory;
    @FXML private ComboBox<SupportedCurrency> paymentCurrency;
    @FXML private Button addPayment;
    @FXML private Label paymentError;
    @FXML private VBox paymentRows;

    public RecurringController(PlanningViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        paymentCurrency.getItems().setAll(SupportedCurrency.values());
        paymentCategory.setConverter(new StringConverter<>() {
            @Override public String toString(Category category) { return category == null ? "" : category.name(); }
            @Override public Category fromString(String input) { throw new UnsupportedOperationException(); }
        });
        addPayment.disableProperty().bind(viewModel.paymentsBusy.or(viewModel.payments.isNull()));
        bindError(paymentError, viewModel.paymentsError);
        addPayment.setOnAction(event -> viewModel.createPayment(paymentName.getText(), paymentAmount.getText(),
                paymentDay.getText(), paymentCategory.getValue(), paymentCurrency.getValue(), () -> {
                    paymentName.clear();
                    paymentAmount.clear();
                    paymentDay.clear();
                }));
        viewModel.payments.addListener((obs, before, after) -> render(after));
    }

    private void render(RecurringData data) {
        if (paymentCurrency.getValue() == null) {
            paymentCurrency.setValue(data.currency());
        }
        Category selected = paymentCategory.getValue();
        if (!paymentCategory.getItems().equals(data.categories())) {
            paymentCategory.getItems().setAll(data.categories());
            paymentCategory.setValue(selected == null ? null : data.categories().stream()
                    .filter(category -> category.id().equals(selected.id())).findFirst().orElse(null));
        }
        paymentRows.getChildren().clear();
        if (data.payments().isEmpty()) {
            paymentRows.getChildren().add(new Label(t("No recurring payments yet.")));
        }
        var dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Messages.locale());
        for (var payment : data.payments()) {
            String category = data.categories().stream().filter(row -> row.id().equals(payment.categoryId()))
                    .map(Category::name).findFirst().orElseGet(() -> t("Unknown category"));
            var title = new Label(payment.name() == null ? category : payment.name());
            title.getStyleClass().add("card-title");
            var detail = new Label(t("{0} · {1} · {2}", category, payment.currency().format(payment.amountMinor()),
                    t("Next due: {0}", dateFormat.format(payment.nextDue(data.today())))));
            detail.setWrapText(true);
            Button archive = new Button(t("Archive"));
            archive.getStyleClass().add("button-secondary");
            archive.disableProperty().bind(viewModel.paymentsBusy);
            archive.setOnAction(event -> confirm(archive, t("Archive this recurring payment?"),
                    () -> viewModel.archivePayment(payment.id())));
            VBox card = new VBox(title, detail, archive);
            card.getStyleClass().addAll("card", "settings-card");
            paymentRows.getChildren().add(card);
        }
    }

    static void bindError(Label label, javafx.beans.value.ObservableValue<String> error) {
        label.textProperty().bind(error);
        label.visibleProperty().bind(label.textProperty().isNotEmpty());
        label.managedProperty().bind(label.visibleProperty());
    }

    static void confirm(Button owner, String message, Runnable action) {
        var accept = new ButtonType(t("Archive"), javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        var cancel = new ButtonType(t("Cancel"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        var prompt = new Alert(Alert.AlertType.CONFIRMATION, message, cancel, accept);
        prompt.setTitle(t("Archive"));
        prompt.setHeaderText(null);
        prompt.initOwner(owner.getScene().getWindow());
        prompt.getDialogPane().setNodeOrientation(owner.getEffectiveNodeOrientation());
        prompt.getDialogPane().getStylesheets().setAll(owner.getScene().getStylesheets());
        ((Button) prompt.getDialogPane().lookupButton(accept)).setDefaultButton(false);
        ((Button) prompt.getDialogPane().lookupButton(cancel)).setDefaultButton(true);
        prompt.showAndWait().filter(accept::equals).ifPresent(result -> action.run());
    }
}
