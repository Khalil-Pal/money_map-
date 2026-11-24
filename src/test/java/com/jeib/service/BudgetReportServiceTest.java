package com.jeib.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.db.Database;
import com.jeib.i18n.Language;
import com.jeib.i18n.Messages;
import com.jeib.model.BudgetPlan;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.Transaction;
import com.jeib.model.TransactionType;
import com.jeib.repository.BudgetPlanRepository;
import com.jeib.repository.CategoryRepository;
import com.jeib.repository.SettingsRepository;
import com.jeib.repository.TransactionRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BudgetReportServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 22);

    @TempDir Path folder;
    private BudgetReportService reports;
    private BudgetPlanService plans;
    private TransactionRepository transactions;
    private String food;

    @BeforeEach
    void setup() throws Exception {
        var database = new Database(folder.resolve("jeib.db"));
        database.migrate();
        var categories = new CategoryService(new CategoryRepository(database, CLOCK));
        food = categories.list(Language.ENGLISH).stream().filter(row -> row.name().equals("Food"))
                .findFirst().orElseThrow().id();
        var currency = new CurrencyService(new SettingsRepository(database));
        currency.setMainCurrency(SupportedCurrency.USD);
        currency.setRate(SupportedCurrency.ILS, "0.25");
        var planRepository = new BudgetPlanRepository(database, CLOCK);
        transactions = new TransactionRepository(database, CLOCK);
        plans = new BudgetPlanService(planRepository, transactions, categories, currency);
        reports = new BudgetReportService(planRepository, transactions, categories, currency, CLOCK);
    }

    @Test
    void usesInclusiveDatesButOpensExportTheDayAfterThePlanEnds() throws Exception {
        var stillRunning = plan("Still running", END.plusDays(1));
        assertFalse(reports.canExport(stillRunning));
        Path result = folder.resolve("unfinished.pdf");
        assertThrows(ValidationException.class,
                () -> reports.export(stillRunning.id(), Language.ENGLISH, BudgetReportService.Format.PDF, result));
        assertFalse(Files.exists(result));
        assertTrue(reports.canExport(plan("Completed", END)));
    }

    @Test
    void exportsOnlyLiveAssignedExpensesAndKeepsOriginalCurrenciesInTheLedger() throws Exception {
        var plan = plan("November", END);
        var other = plan("Another plan", END);
        add(plan, START, 1_025, "USD", "First");
        add(plan, END, 8_000, "ILS", "Last");
        add(other, END, 4_000, "USD", "Another plan");
        add(plan, END.plusDays(1), 4_000, "USD", "Outside");
        transactions.softDelete(add(plan, END, 5_000, "USD", "Deleted").id());

        var report = reports.load(plan.id(), Language.ARABIC);
        assertEquals(3_025, report.summary().spentMinor());
        assertEquals(6_975, report.summary().remainingMinor());
        assertEquals(List.of("Last", "First"), report.transactions().stream().map(Transaction::note).toList());
        assertEquals(Messages.translate(Language.ARABIC, "Food"), report.category(report.transactions().getFirst()));

        Path excel = folder.resolve("report.xlsx");
        reports.export(plan.id(), Language.ARABIC, BudgetReportService.Format.EXCEL, excel);
        try (var workbook = new XSSFWorkbook(Files.newInputStream(excel))) {
            assertEquals(2, workbook.getNumberOfSheets());
            var summary = workbook.getSheetAt(0);
            assertTrue(summary.isRightToLeft());
            assertEquals(2, summary.getDrawingPatriarch().getCharts().size());
            assertEquals("30.25", summary.getRow(5).getCell(1).getRawValue());
            var ledger = workbook.getSheetAt(1);
            assertEquals(2, ledger.getLastRowNum());
            assertEquals("ILS", ledger.getRow(1).getCell(2).getStringCellValue());
            assertEquals("80.00", ledger.getRow(1).getCell(3).getRawValue());
        }

        Path pdf = folder.resolve("report.pdf");
        reports.export(plan.id(), Language.ARABIC, BudgetReportService.Format.PDF, pdf);
        String contents = Files.readString(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(contents.startsWith("%PDF-1.4"));
        assertTrue(contents.contains("/DeviceRGB"));
        assertTrue(contents.contains("/ActualText"));
        assertTrue(contents.endsWith("%%EOF\n"));
    }

    private BudgetPlan plan(String name, LocalDate end) throws Exception {
        return plans.create(name, START, end, "100", SupportedCurrency.USD, Set.of(food));
    }

    private Transaction add(BudgetPlan plan, LocalDate date, long minor, String currency, String note) throws Exception {
        var transaction = Transaction.create(food, TransactionType.EXPENSE, minor, currency, date, note, CLOCK, plan.id());
        transactions.insert(transaction);
        return transaction;
    }
}
