package com.jeib.ui;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import javafx.concurrent.Task;

/**
 * Opens a folder, a web page, or an email draft through the OS, off the JavaFX thread.
 */
final class Links {

    private Links() {
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

    static void mail(Supplier<String> draftUrl, Runnable onUnavailable) {
        Task<Void> openDraft = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                    throw new UnsupportedOperationException();
                }
                Desktop.getDesktop().mail(URI.create(draftUrl.get()));
                return null;
            }
        };
        openDraft.setOnFailed(event -> onUnavailable.run());
        Thread.ofVirtual().name("jeib-mail").start(openDraft);
    }

    static void browse(String url, Runnable onUnavailable) {
        Task<Void> openPage = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw new UnsupportedOperationException();
                }
                Desktop.getDesktop().browse(URI.create(url));
                return null;
            }
        };
        openPage.setOnFailed(event -> onUnavailable.run());
        Thread.ofVirtual().name("jeib-browse").start(openPage);
    }
}
