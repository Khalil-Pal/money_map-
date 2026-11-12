package com.jeib.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.List;
import java.util.function.Consumer;

/**
 * The crash log (feature 37): any error nothing else caught is written to {@code logs/jeib.log}
 * next to the database, with the time, Jeib's version, Java, and Windows version, and the stack
 * trace. "Report a problem" attaches its last lines to a GitHub issue. The file is kept small: past
 * {@link #MAX_BYTES} it's renamed to jeib.log.1 (replacing the older one) and starts again.
 */
public final class CrashLog {

    static final long MAX_BYTES = 512 * 1024;

    private final Path file;
    private final Clock clock;
    private volatile Consumer<Throwable> listener = error -> { };

    public CrashLog(Path dataFolder, Clock clock) {
        this.file = dataFolder.resolve("logs").resolve("jeib.log");
        this.clock = clock;
    }

    public Path file() {
        return file;
    }

    /** Catches every uncaught error, on every thread (including the JavaFX one), from now on. */
    public void install() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> record(thread.getName(), error));
    }

    /** Called after an error is written, e.g. to show a toast; runs on the thread that failed. */
    public void onCrash(Consumer<Throwable> listener) {
        this.listener = listener;
    }

    /** Writes one error. Never throws: a broken log must not hide the original problem. */
    public void record(String threadName, Throwable error) {
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file) && Files.size(file) > MAX_BYTES) {
                Files.move(file, file.resolveSibling("jeib.log.1"), StandardCopyOption.REPLACE_EXISTING);
            }
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            String entry = "=== " + clock.instant() + " · Jeib " + AppInfo.VERSION + " · Java "
                    + System.getProperty("java.version") + " · " + System.getProperty("os.name") + " "
                    + System.getProperty("os.version") + " · thread " + threadName + "\n" + trace + "\n";
            Files.writeString(file, entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
            // Nothing sensible left to do; the error still reaches the listener below.
        }
        try {
            listener.accept(error);
        } catch (RuntimeException ignored) {
            // A failing listener must not loop back into the handler.
        }
    }

    /** The last {@code count} lines of the log, for a problem report; empty if there's no log. */
    public List<String> lastLines(int count) {
        try {
            if (!Files.exists(file)) {
                return List.of();
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            return lines.subList(Math.max(0, lines.size() - count), lines.size());
        } catch (IOException e) {
            return List.of();
        }
    }
}
