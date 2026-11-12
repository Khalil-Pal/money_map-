package com.jeib.ui;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opens a web page in the user's browser or a folder in Explorer, off the JavaFX thread (the
 * system call can take a moment). Failures are ignored: there's nothing useful to show if Windows
 * can't open a browser.
 */
final class Links {

    private Links() {
    }

    static void browse(String url) {
        Thread.ofVirtual().name("jeib-browse").start(() -> {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI.create(url));
                }
            } catch (Exception ignored) {
                // No browser available.
            }
        });
    }

    static void openFolder(Path folder) {
        Thread.ofVirtual().name("jeib-open-folder").start(() -> {
            try {
                Files.createDirectories(folder);
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(new File(folder.toString()));
                }
            } catch (Exception ignored) {
                // Explorer not available.
            }
        });
    }
}
