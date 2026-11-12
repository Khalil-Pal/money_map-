package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.service.BackupService;
import com.jeib.service.BackupService.Backup;
import java.util.List;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

/**
 * The Backups card in Settings: the daily copies, "Back up now", and restore. Restoring replaces
 * all data, so it asks first (the picked backup becomes "pending") and then reloads the whole app.
 */
public final class BackupsViewModel {

    @FunctionalInterface
    private interface Work<T> {
        T run() throws Exception;
    }

    private final BackupService backups;
    private final Runnable reloadApp;
    private final Consumer<String> report;

    private final ObservableList<Backup> list = FXCollections.observableArrayList();
    private final ObjectProperty<Backup> pendingRestore = new SimpleObjectProperty<>();
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    /**
     * @param reloadApp rebuilds the window after a restore, so everything reads the restored data
     * @param report    shows a short message (a toast)
     */
    public BackupsViewModel(BackupService backups, Runnable reloadApp, Consumer<String> report) {
        this.backups = backups;
        this.reloadApp = reloadApp;
        this.report = report;
    }

    /** The daily backup: makes today's copy if needed (run once the database is ready). */
    public void backupIfNeeded() {
        run(() -> {
            backups.backupIfNeeded();
            return backups.list();
        }, list::setAll);
    }

    public void load() {
        run(backups::list, list::setAll);
    }

    public void backUpNow() {
        run(() -> {
            backups.backupIfNeeded();
            return backups.list();
        }, loaded -> {
            list.setAll(loaded);
            report.accept(t("Today's backup is saved."));
        });
    }

    public void askRestore(Backup backup) {
        pendingRestore.set(backup);
    }

    public void cancelRestore() {
        pendingRestore.set(null);
    }

    public void confirmRestore() {
        Backup chosen = pendingRestore.get();
        if (chosen == null) {
            return;
        }
        run(() -> backups.restore(chosen), safety -> {
            pendingRestore.set(null);
            reloadApp.run();
        });
    }

    private <T> void run(Work<T> work, Consumer<T> onDone) {
        if (busy.get()) {
            return;
        }
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.run();
            }
        };
        task.setOnSucceeded(event -> {
            busy.set(false);
            onDone.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            busy.set(false);
            error.set(t("Something went wrong: {0}", task.getException().getMessage()));
        });
        error.set("");
        busy.set(true);
        Thread.ofVirtual().name("jeib-backup").start(task);
    }

    public ObservableList<Backup> backups() {
        return list;
    }

    public ReadOnlyObjectProperty<Backup> pendingRestoreProperty() {
        return pendingRestore;
    }

    public ReadOnlyStringProperty errorProperty() {
        return error;
    }

    public ReadOnlyBooleanProperty busyProperty() {
        return busy;
    }

    public java.nio.file.Path folder() {
        return backups.folder();
    }
}
