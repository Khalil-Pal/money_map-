package com.jeib.service;

import com.jeib.db.Database;
import com.jeib.db.Timestamps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Automatic daily backups (feature 16): the first time Jeib opens on a day, it saves a copy of the
 * database as {@code backups/jeib-YYYY-MM-DD.db} next to it and keeps the newest {@link #KEEP}.
 * Any of them can be restored from Settings.
 *
 * <p>Copies are made with SQLite's {@code VACUUM INTO}, which writes a consistent snapshot even while
 * the database is in use (copying the file directly could catch it halfway through a write). The
 * copy goes to a ".part" file first and is renamed when complete, so a crash never leaves a broken
 * backup that looks real.
 */
public final class BackupService {

    public static final int KEEP = 7;

    private static final Pattern NAME = Pattern.compile("jeib-(\\d{4}-\\d{2}-\\d{2})\\.db");

    private final Database database;
    private final Path folder;
    private final Clock clock;

    /** A backup file and the day it was made. */
    public record Backup(LocalDate day, Path file, long sizeBytes) {
    }

    public BackupService(Database database, Clock clock) {
        this.database = database;
        this.folder = database.file().getParent().resolve("backups");
        this.clock = clock;
    }

    public Path folder() {
        return folder;
    }

    /**
     * Makes today's backup if there isn't one yet, then deletes all but the newest {@link #KEEP}.
     *
     * @return the new backup, or empty if today's already existed
     */
    public Optional<Backup> backupIfNeeded() throws SQLException, IOException {
        LocalDate today = LocalDate.now(clock);
        Path target = folder.resolve("jeib-" + today + ".db");
        Optional<Backup> made = Optional.empty();
        if (!Files.exists(target)) {
            copyTo(target);
            made = Optional.of(new Backup(today, target, Files.size(target)));
        }
        prune();
        return made;
    }

    /** Backups on disk, newest first. Files with other names in the folder are ignored. */
    public List<Backup> list() throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        List<Backup> backups = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.toList()) {
                Matcher name = NAME.matcher(file.getFileName().toString());
                if (name.matches()) {
                    backups.add(new Backup(LocalDate.parse(name.group(1)), file, Files.size(file)));
                }
            }
        }
        backups.sort(Comparator.comparing(Backup::day).reversed());
        return backups;
    }

    /**
     * Replaces the database with {@code backup}. First the current database is saved as
     * {@code backups/jeib-before-restore-<time>.db}, so a restore can itself be undone by hand.
     * The app must reload afterwards (nothing may still hold data from before).
     *
     * @return where the pre-restore copy was saved
     */
    public Path restore(Backup backup) throws SQLException, IOException {
        if (!Files.isRegularFile(backup.file())) {
            throw new ValidationException(com.jeib.i18n.Messages.t("That backup file is gone."));
        }
        String stamp = Timestamps.now(clock).replace(':', '-').replace('.', '-');
        Path safety = folder.resolve("jeib-before-restore-" + stamp + ".db");
        copyTo(safety);
        Files.copy(backup.file(), database.file(), StandardCopyOption.REPLACE_EXISTING);
        return safety;
    }

    private void copyTo(Path target) throws SQLException, IOException {
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.deleteIfExists(part); // left over from an interrupted copy
        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            // VACUUM INTO takes a file name, not a bound parameter; quote it as an SQL string literal.
            statement.execute("VACUUM INTO '" + part.toString().replace("'", "''") + "'");
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void prune() throws IOException {
        List<Backup> all = list();
        for (Backup old : all.subList(Math.min(KEEP, all.size()), all.size())) {
            Files.deleteIfExists(old.file());
        }
    }
}
