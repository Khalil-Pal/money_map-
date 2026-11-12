package com.jeib.service;

import java.sql.SQLException;

/**
 * One user change that can be undone and redone (Command pattern). Commands go through the
 * service layer, so an undo is a normal, validated, synced change: undoing a delete restores the
 * soft-deleted row and bumps its version, it never rewrites history.
 */
public interface Command {

    /** Does the change. Called once by {@link UndoManager#perform} and again on every redo. */
    void execute() throws SQLException;

    /** Reverses what {@link #execute} did. */
    void undo() throws SQLException;

    /** What the user did, for messages like "Undone: Delete transaction". */
    String description();
}
