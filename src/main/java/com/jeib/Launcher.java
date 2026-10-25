package com.jeib;

/**
 * Entry point for the jar and the jpackage build. JavaFX refuses to start from a main class that
 * extends Application when it's loaded from the classpath, so this class only delegates.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        JeibApp.main(args);
    }
}
