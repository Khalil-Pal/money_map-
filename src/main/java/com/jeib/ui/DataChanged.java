package com.jeib.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * One signal for "the data changed" (a save, delete, undo, quick add, limit change). Everything
 * that shows data listens: the list reloads, the Budgets page reloads, limit alerts are checked.
 * Senders just call {@link #run()} and don't need to know who listens.
 */
public final class DataChanged implements Runnable {

    private final List<Runnable> listeners = new ArrayList<>();

    public void listen(Runnable listener) {
        listeners.add(listener);
    }

    /** Call on the JavaFX thread. */
    @Override
    public void run() {
        listeners.forEach(Runnable::run);
    }
}
