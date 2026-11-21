package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.service.TransactionHistoryReset;
import java.nio.file.Path;
import java.util.function.Consumer;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;

final class DangerZoneViewModel {

    final SimpleBooleanProperty busy = new SimpleBooleanProperty();
    final SimpleStringProperty message = new SimpleStringProperty("");
    private final TransactionHistoryReset reset;

    DangerZoneViewModel(TransactionHistoryReset reset) {
        this.reset = reset;
    }

    void run(Path destination, Consumer<String> onSuccess) {
        if (busy.get()) {
            return;
        }
        var task = new Task<Integer>() {
            @Override
            protected Integer call() throws Exception {
                return reset.execute(destination);
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(destination == null ? t("Transaction history cleared.")
                : t("PDF saved and transaction history cleared.")));
        task.setOnFailed(event -> {
            busy.set(false);
            message.set(t("Could not clear transactions: {0}", task.getException().getMessage()));
        });
        busy.set(true);
        message.set(destination == null ? t("Clearing transactions…") : t("Exporting PDF…"));
        Thread.ofVirtual().name("jeib-reset-history").start(task);
    }
}
