package com.jeib.service;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * The undo and redo stacks for one app session (they start empty at every launch).
 *
 * <ul>
 *   <li>{@link #perform}: run a new command; on success it goes on the undo stack and the redo
 *       stack is cleared (a new change makes the old "future" meaningless).</li>
 *   <li>{@link #undo}: undo the newest command and move it to the redo stack.</li>
 *   <li>{@link #redo}: run it again and move it back.</li>
 * </ul>
 * A command that fails (e.g. a validation error) changes nothing and is not recorded. If an undo or
 * redo fails, that command is dropped, because the data no longer matches what it remembers.
 *
 * <p>Synchronized: background tasks call it, and two quick Ctrl+Z presses must not interleave.
 */
public final class UndoManager {

    /** Oldest changes are forgotten beyond this, so memory stays small in a long session. */
    public static final int LIMIT = 100;

    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();

    public synchronized void perform(Command command) throws SQLException {
        command.execute(); // throws → nothing recorded
        undoStack.push(command);
        if (undoStack.size() > LIMIT) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    /** @return the undone command's description, or empty if there was nothing to undo */
    public synchronized Optional<String> undo() throws SQLException {
        Command command = undoStack.poll();
        if (command == null) {
            return Optional.empty();
        }
        command.undo(); // throws → the command is dropped
        redoStack.push(command);
        return Optional.of(command.description());
    }

    /** @return the redone command's description, or empty if there was nothing to redo */
    public synchronized Optional<String> redo() throws SQLException {
        Command command = redoStack.poll();
        if (command == null) {
            return Optional.empty();
        }
        command.execute();
        undoStack.push(command);
        return Optional.of(command.description());
    }

    public synchronized boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public synchronized boolean canRedo() {
        return !redoStack.isEmpty();
    }
}
