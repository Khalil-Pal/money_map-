package com.jeib.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.db.Database;
import com.jeib.model.DateRange;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.Transaction;
import com.jeib.model.TransactionType;
import com.jeib.repository.BudgetPlanRepository;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.RecurringRepository;
import com.jeib.repository.SavingsGoalRepository;
import com.jeib.repository.SettingsRepository;
import com.jeib.repository.TransactionRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransactionHistoryResetTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.now(CLOCK);
    private static final DateRange RANGE = new DateRange(TODAY, TODAY);

    @TempDir Path folder;
    private Database database;
    private TransactionRepository transactions;
    private BudgetPlanRepository plans;
    private TransactionCommands commands;
    private TransactionHistoryReset reset;
    private String category;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(folder.resolve("jeib.db"));
        database.migrate();
        var categoryRepo = new CategoryRepository(database, CLOCK);
        var categories = new CategoryService(categoryRepo);
        var expense = categories.list().stream().filter(row -> row.kind() == TransactionType.EXPENSE).findFirst().orElseThrow();
        category = expense.id();
        var currencies = new CurrencyService(new SettingsRepository(database));
        currencies.setMainCurrency(SupportedCurrency.USD);
        transactions = new TransactionRepository(database, CLOCK);
        plans = new BudgetPlanRepository(database, CLOCK);
        var budgets = new BudgetPlanService(plans, transactions, categories, currencies);
        budgets.create("Trip 旅行", TODAY, TODAY, "100", SupportedCurrency.USD, Set.of(category));
        var planning = new PlanningService(new RecurringRepository(database, CLOCK),
                new SavingsGoalRepository(database, CLOCK), categories, currencies, CLOCK);
        planning.createPayment("Internet bill", "25", "31", expense, SupportedCurrency.USD);
        planning.createGoal("Laptop savings", "1000", SupportedCurrency.USD);
        commands = new TransactionCommands(new TransactionService(transactions, categoryRepo, currencies, CLOCK, plans),
                new UndoManager());
        reset = new TransactionHistoryReset(database, commands.history(), CLOCK);
    }

    @Test
    void failureToSaveThePdfKeepsAllTransactionsAndUndo() throws Exception {
        add("Original");
        Path existing = folder.resolve("existing.pdf");
        Files.writeString(existing, "do not overwrite");

        assertThrows(IOException.class, () -> reset.execute(existing));
        assertEquals("do not overwrite", Files.readString(existing));
        assertEquals(1, transactions.findByRange(RANGE).size());
        assertTrue(commands.history().canUndo());
    }

    @Test
    void exportsAcrossPagesBeforeClearingAndKeepsOtherData() throws Exception {
        var first = add("غداء Lunch");
        for (int i = 0; i < 65; i++) {
            add("Example " + i);
        }
        Path pdf = folder.resolve("export.pdf");

        assertEquals(66, reset.execute(pdf));
        String document = Files.readString(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(document.startsWith("%PDF-1.4"));
        var pages = Pattern.compile("/Type /Pages /Count (\\d+)").matcher(document);
        assertTrue(pages.find());
        assertTrue(Integer.parseInt(pages.group(1)) > 1);
        for (String content : new String[] {"غداء Lunch", "Trip 旅行", "Internet bill", "Laptop savings"}) {
            assertTrue(document.contains(HexFormat.of().formatHex(content.getBytes(StandardCharsets.UTF_16BE))), content);
        }
        assertTrue(transactions.findByRange(RANGE).isEmpty());
        assertEquals(2, transactions.findById(first.id()).orElseThrow().version());
        assertFalse(commands.history().canUndo());
        assertFalse(commands.history().canRedo());
        assertEquals(1, plans.list().size());
        assertEquals("USD", new SettingsRepository(database).get(CurrencyService.MAIN_KEY).orElseThrow());
    }

    @Test
    void canClearWithoutCreatingAFile() throws Exception {
        add("Original");
        add("Another");
        commands.history().undo();
        assertTrue(commands.history().canRedo());
        assertEquals(1, reset.execute(null));
        assertTrue(transactions.findByRange(RANGE).isEmpty());
        assertFalse(commands.history().canUndo());
        assertFalse(commands.history().canRedo());
        try (var files = Files.list(folder)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".pdf")));
        }
    }

    private Transaction add(String note) throws SQLException {
        return commands.add(new TransactionInput(TransactionType.EXPENSE, "12.50", SupportedCurrency.USD,
                TODAY, category, note));
    }
}
