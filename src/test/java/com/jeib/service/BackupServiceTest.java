package com.jeib.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.db.Database;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.TransactionType;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.SettingsRepository;
import com.jeib.repository.TransactionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Daily backups and restore, on real files. */
class BackupServiceTest {

    private static final String FOOD = "a87a6066-c95c-48d4-b8c7-849a88bcab1f";

    @TempDir
    Path tempDir;

    private Database database;

    @BeforeEach
    void setUp() throws SQLException {
        database = new Database(tempDir.resolve("jeib.db"));
        database.migrate();
        new CurrencyService(new SettingsRepository(database)).setMainCurrency(SupportedCurrency.RUB);
    }

    private static Clock on(String day) {
        return Clock.fixed(Instant.parse(day + "T10:00:00Z"), ZoneOffset.UTC);
    }

    private TransactionService transactions(Clock clock) {
        return new TransactionService(new TransactionRepository(database, clock), new CategoryRepository(database, clock),
                new CurrencyService(new SettingsRepository(database)), clock);
    }

    private void spend(String amount, Clock clock) throws SQLException {
        transactions(clock).add(new TransactionInput(TransactionType.EXPENSE, amount, null, LocalDate.now(clock), FOOD, null));
    }

    @Test
    void theFirstLaunchOfADayMakesOneBackup() throws Exception {
        BackupService backups = new BackupService(database, on("2026-11-12"));

        assertTrue(backups.backupIfNeeded().isPresent());
        assertTrue(backups.backupIfNeeded().isEmpty()); // same day: nothing new

        List<BackupService.Backup> list = backups.list();
        assertEquals(1, list.size());
        assertEquals(LocalDate.parse("2026-11-12"), list.getFirst().day());
        assertEquals("jeib-2026-11-12.db", list.getFirst().file().getFileName().toString());
        assertTrue(list.getFirst().sizeBytes() > 0);
    }

    @Test
    void aBackupIsAWorkingCopyOfTheData() throws Exception {
        spend("250", on("2026-11-12"));
        BackupService.Backup backup = new BackupService(database, on("2026-11-12")).backupIfNeeded().orElseThrow();

        Database copy = new Database(backup.file());
        assertEquals(1, new TransactionService(new TransactionRepository(copy, on("2026-11-12")),
                new CategoryRepository(copy, on("2026-11-12")), new CurrencyService(new SettingsRepository(copy)),
                on("2026-11-12")).listMonth(YearMonth.of(2026, 11)).size());
    }

    @Test
    void onlyTheNewestSevenAreKept() throws Exception {
        for (int day = 1; day <= 9; day++) {
            new BackupService(database, on("2026-11-0" + day)).backupIfNeeded();
        }

        List<LocalDate> days = new BackupService(database, on("2026-11-09")).list().stream()
                .map(BackupService.Backup::day).toList();

        assertEquals(BackupService.KEEP, days.size());
        assertEquals(LocalDate.parse("2026-11-09"), days.getFirst());
        assertEquals(LocalDate.parse("2026-11-03"), days.getLast()); // the 1st and 2nd were deleted
    }

    @Test
    void restoringBringsBackTheOldDataAndKeepsASafetyCopy() throws Exception {
        spend("100", on("2026-11-11"));
        BackupService.Backup monday = new BackupService(database, on("2026-11-11")).backupIfNeeded().orElseThrow();
        spend("999", on("2026-11-12")); // a change after the backup

        Path safety = new BackupService(database, on("2026-11-12")).restore(monday);

        assertEquals(List.of(10_000L), transactions(on("2026-11-12")).listMonth(YearMonth.of(2026, 11)).stream()
                .map(t -> t.amountMinor()).toList());
        assertTrue(Files.exists(safety));
        assertTrue(safety.getFileName().toString().startsWith("jeib-before-restore-"));
    }

    @Test
    void noHalfWrittenFilesAreLeftBehind() throws Exception {
        BackupService backups = new BackupService(database, on("2026-11-12"));
        backups.backupIfNeeded();

        try (Stream<Path> files = Files.list(backups.folder())) {
            assertFalse(files.anyMatch(f -> f.toString().endsWith(".part")));
        }
    }

    @Test
    void anEmptyFolderMeansNoBackups() throws IOException {
        assertEquals(List.of(), new BackupService(database, on("2026-11-12")).list());
    }
}
