package com.jeib.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The stacks alone, with commands that only write to a log. */
class UndoManagerTest {

    private final List<String> log = new ArrayList<>();
    private final UndoManager history = new UndoManager();

    private Command command(String name) {
        return new Command() {
            @Override
            public void execute() {
                log.add("do " + name);
            }

            @Override
            public void undo() {
                log.add("undo " + name);
            }

            @Override
            public String description() {
                return name;
            }
        };
    }

    @Test
    void undoAndRedoGoBackwardsAndForwards() throws SQLException {
        history.perform(command("A"));
        history.perform(command("B"));

        assertEquals(Optional.of("B"), history.undo());
        assertEquals(Optional.of("A"), history.undo());
        assertEquals(Optional.of("A"), history.redo());

        assertEquals(List.of("do A", "do B", "undo B", "undo A", "do A"), log);
        assertTrue(history.canUndo());
        assertTrue(history.canRedo());
    }

    @Test
    void nothingToUndoOrRedoIsReportedAsEmpty() throws SQLException {
        assertEquals(Optional.empty(), history.undo());
        assertEquals(Optional.empty(), history.redo());
        assertFalse(history.canUndo());
    }

    @Test
    void aNewChangeClearsTheRedoStack() throws SQLException {
        history.perform(command("A"));
        history.undo();
        assertTrue(history.canRedo());

        history.perform(command("B"));

        assertFalse(history.canRedo());
        assertEquals(Optional.empty(), history.redo());
    }

    @Test
    void aFailedCommandIsNotRecorded() throws SQLException {
        Command failing = new Command() {
            @Override
            public void execute() {
                throw new ValidationException("amount", "Enter an amount.");
            }

            @Override
            public void undo() {
                log.add("must never run");
            }

            @Override
            public String description() {
                return "failing";
            }
        };

        assertThrows(ValidationException.class, () -> history.perform(failing));

        assertFalse(history.canUndo());
        assertEquals(Optional.empty(), history.undo());
    }

    @Test
    void onlyTheNewestChangesAreKept() throws SQLException {
        for (int i = 0; i < UndoManager.LIMIT + 5; i++) {
            history.perform(command("C" + i));
        }
        int undone = 0;
        while (history.undo().isPresent()) {
            undone++;
        }
        assertEquals(UndoManager.LIMIT, undone);
        assertEquals("undo C5", log.getLast()); // C0-C4 were forgotten
    }
}
