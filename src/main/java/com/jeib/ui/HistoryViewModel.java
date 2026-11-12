package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.service.UndoManager;
import com.jeib.service.ValidationException;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.concurrent.Task;

/** Ctrl+Z / Ctrl+Y: undo and redo the last changes, in the background, then tell the user what happened. */
public final class HistoryViewModel {

    private final UndoManager history;
    private final Runnable afterChange;
    private final Consumer<String> report;

    /**
     * @param afterChange runs on the JavaFX thread after data changed, e.g. reload the list
     * @param report      shows a short message to the user (a toast)
     */
    public HistoryViewModel(UndoManager history, Runnable afterChange, Consumer<String> report) {
        this.history = history;
        this.afterChange = afterChange;
        this.report = report;
    }

    public void undo() {
        run(history::undo, "Undone: {0}", "Nothing to undo.", "Can't undo: {0}");
    }

    public void redo() {
        run(history::redo, "Redone: {0}", "Nothing to redo.", "Can't redo: {0}");
    }

    @FunctionalInterface
    private interface Step {
        Optional<String> run() throws SQLException;
    }

    private void run(Step step, String done, String nothing, String failed) {
        Task<Optional<String>> task = new Task<>() {
            @Override
            protected Optional<String> call() throws Exception {
                return step.run();
            }
        };
        task.setOnSucceeded(event -> {
            Optional<String> description = task.getValue();
            if (description.isPresent()) {
                afterChange.run();
                report.accept(t(done, description.get()));
            } else {
                report.accept(t(nothing));
            }
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            report.accept(error instanceof ValidationException
                    ? t(failed, error.getMessage())
                    : t("Something went wrong: {0}", error.getMessage()));
            afterChange.run(); // the list may be out of date; show what's really stored
        });
        Thread.ofVirtual().name("jeib-history").start(task);
    }
}
