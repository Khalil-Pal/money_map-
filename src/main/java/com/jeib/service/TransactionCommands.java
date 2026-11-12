package com.jeib.service;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.Transaction;
import java.sql.SQLException;

/**
 * Adds, edits, and deletes transactions as undoable commands. The UI uses this instead of calling
 * {@link TransactionService} directly, so every change lands on the undo stack. The rules are still
 * the service's: each step below is a normal, validated service call.
 */
public final class TransactionCommands {

    private final TransactionService transactions;
    private final UndoManager history;

    public TransactionCommands(TransactionService transactions, UndoManager history) {
        this.transactions = transactions;
        this.history = history;
    }

    /** The undo history these commands are recorded in. */
    public UndoManager history() {
        return history;
    }

    public Transaction add(TransactionInput input) throws SQLException {
        Add command = new Add(input);
        history.perform(command);
        return command.added;
    }

    public Transaction edit(String id, TransactionInput input) throws SQLException {
        Edit command = new Edit(id, input);
        history.perform(command);
        return command.after;
    }

    public void delete(String id) throws SQLException {
        history.perform(new Delete(id));
    }

    /** First run adds; undo soft-deletes it; redo restores the same row (same id, never a duplicate). */
    private final class Add implements Command {
        private final TransactionInput input;
        private Transaction added;

        Add(TransactionInput input) {
            this.input = input;
        }

        @Override
        public void execute() throws SQLException {
            if (added == null) {
                added = transactions.add(input);
            } else {
                transactions.restore(added.id());
            }
        }

        @Override
        public void undo() throws SQLException {
            transactions.delete(added.id());
        }

        @Override
        public String description() {
            return t("Add transaction");
        }
    }

    /** Remembers the values before and after; undo and redo re-apply one or the other. */
    private final class Edit implements Command {
        private final String id;
        private final TransactionInput input;
        private TransactionInput before;
        private Transaction after;

        Edit(String id, TransactionInput input) {
            this.id = id;
            this.input = input;
        }

        @Override
        public void execute() throws SQLException {
            if (before == null) {
                Transaction current = transactions.find(id)
                        .orElseThrow(() -> new ValidationException(t("This transaction no longer exists.")));
                before = TransactionService.inputOf(current);
                after = transactions.edit(id, input);
            } else {
                after = transactions.edit(id, TransactionService.inputOf(after));
            }
        }

        @Override
        public void undo() throws SQLException {
            transactions.edit(id, before);
        }

        @Override
        public String description() {
            return t("Edit transaction");
        }
    }

    private final class Delete implements Command {
        private final String id;

        Delete(String id) {
            this.id = id;
        }

        @Override
        public void execute() throws SQLException {
            transactions.delete(id);
        }

        @Override
        public void undo() throws SQLException {
            transactions.restore(id);
        }

        @Override
        public String description() {
            return t("Delete transaction");
        }
    }
}
