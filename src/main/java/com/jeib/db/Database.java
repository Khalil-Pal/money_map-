package com.jeib.db;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;

/** The local SQLite database. Schema changes only happen through Flyway migrations in db/migration. */
public final class Database {

    private final Path file;
    private final String url;

    public Database(Path file) {
        this.file = file.toAbsolutePath();
        this.url = "jdbc:sqlite:" + this.file + "?foreign_keys=on";
    }

    /** The database in the user's app-data folder, e.g. %APPDATA%\Jeib\jeib.db on Windows. */
    public static Database openDefault() {
        Path dir = dataDirectory();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Can't create " + dir, e);
        }
        return new Database(dir.resolve("jeib.db"));
    }

    static Path dataDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Path.of(appData != null ? appData : home, "Jeib");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "Jeib");
        }
        return Path.of(home, ".local", "share", "jeib");
    }

    /** Brings the schema up to date. Slow on first run, so call it off the JavaFX thread. */
    public void migrate() {
        Flyway.configure()
                .dataSource(url, null, null)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    public Path file() {
        return file;
    }
}
