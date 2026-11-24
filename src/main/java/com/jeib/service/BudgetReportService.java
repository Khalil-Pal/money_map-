package com.jeib.service;

import com.jeib.i18n.Language;
import com.jeib.i18n.Messages;
import com.jeib.model.BudgetPlan;
import com.jeib.model.Category;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.Transaction;
import com.jeib.repository.BudgetPlanRepository;
import com.jeib.repository.TransactionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class BudgetReportService {

    public enum Format { EXCEL, PDF }

    record Report(BudgetPlanService.Summary summary, List<Transaction> transactions,
                  Map<String, Category> categories, Language language) {
        BudgetPlan plan() {
            return summary.plan();
        }

        String t(String key, Object... args) {
            return Messages.translate(language, key, args);
        }

        String date(LocalDate date) {
            return Messages.date(language, date, language.rightToLeft() ? FormatStyle.LONG : FormatStyle.MEDIUM);
        }

        String period() {
            return t("{0} to {1}", date(plan().dates().start()), date(plan().dates().end()));
        }

        String amount(long minor, SupportedCurrency currency) {
            return currency.formatPlain(minor);
        }

        String category(Transaction transaction) {
            return categories.get(transaction.categoryId()).name();
        }

        List<BudgetService.CategorySpending> spending() {
            return summary.byCategory().stream().filter(row -> row.spentMinor() > 0)
                    .sorted(Comparator.comparingLong(BudgetService.CategorySpending::spentMinor).reversed())
                    .toList();
        }

        String missingRates() {
            return summary.missingRates().stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
        }
    }

    private final BudgetPlanRepository plans;
    private final TransactionRepository transactions;
    private final CategoryService categories;
    private final CurrencyService currencies;
    private final Clock clock;

    public BudgetReportService(BudgetPlanRepository plans, TransactionRepository transactions,
                               CategoryService categories, CurrencyService currencies, Clock clock) {
        this.plans = plans;
        this.transactions = transactions;
        this.categories = categories;
        this.currencies = currencies;
        this.clock = clock;
    }

    public boolean canExport(BudgetPlan plan) {
        return LocalDate.now(clock).isAfter(plan.dates().end());
    }

    Report load(String planId, Language language) throws SQLException {
        var plan = plans.find(planId).orElseThrow(() -> new ValidationException(
                Messages.translate(language, "This budget plan no longer exists.")));
        if (!canExport(plan)) {
            throw new ValidationException(Messages.translate(language, "Reports are available after the plan ends."));
        }
        var rows = transactions.findByRange(plan.dates(), plan.id());
        var categoryList = new ArrayList<>(categories.list(language));
        Map<String, Category> byId = new HashMap<>();
        categoryList.forEach(category -> byId.put(category.id(), category));
        for (Transaction row : rows) {
            if (!byId.containsKey(row.categoryId())) {
                var fallback = new Category(row.categoryId(), Messages.translate(language, "Unknown category"),
                        row.type(), 6, false);
                byId.put(fallback.id(), fallback);
                categoryList.add(fallback);
            }
        }
        var summary = BudgetPlanService.summarize(plan, LocalDate.now(clock), rows, categoryList, currencies.rates());
        return new Report(summary, List.copyOf(rows), Map.copyOf(byId), language);
    }

    public void export(String planId, Language language, Format format, Path destination)
            throws SQLException, IOException {
        Report report = load(planId, language);
        Path target = destination.toAbsolutePath().normalize();
        Path temporary = Files.createTempFile(target.getParent(), ".jeib-report-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                switch (format) {
                    case EXCEL -> BudgetReportWorkbook.write(report, output);
                    case PDF -> BudgetReportPdf.write(report, output);
                }
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
