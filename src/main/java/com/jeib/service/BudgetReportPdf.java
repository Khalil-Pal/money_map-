package com.jeib.service;

import com.jeib.model.SupportedCurrency;
import java.io.IOException;
import java.io.OutputStream;

final class BudgetReportPdf {
    private BudgetReportPdf() {
    }

    static void write(BudgetReportService.Report report, OutputStream output) throws IOException {
        var colors = new ReportColors();
        try (var pdf = new HistoryPdf(output, report.language().rightToLeft(), colors)) {
            var summary = report.summary();
            var currency = report.plan().currency();
            pdf.row("Jeib  /  " + report.t("Budget report"));
            pdf.title(report.plan().name());
            pdf.row(report.period());
            pdf.pair(report.t("Remaining"), report.amount(summary.remainingMinor(), currency));
            pdf.heading(report.t("Budget vs. spending"));
            long maximum = Math.max(report.plan().limitMinor(), summary.spentMinor());
            pdf.bar(report.t("Budget limit"), report.amount(report.plan().limitMinor(), currency),
                    report.plan().limitMinor(), maximum, colors.get("border-strong"));
            pdf.bar(report.t("Total spent"), report.amount(summary.spentMinor(), currency),
                    summary.spentMinor(), maximum, colors.get("primary"));
            pdf.row(report.t("Totals use the exchange rates saved at export."));
            if (!summary.missingRates().isEmpty()) {
                pdf.row(report.t("Some spending is missing exchange rates: {0}.", report.missingRates()));
            }

            pdf.heading(report.t("Spending by category"));
            var spending = report.spending();
            if (spending.isEmpty()) {
                pdf.row(report.t("No spending to chart."));
            } else {
                long highest = spending.getFirst().spentMinor();
                for (var category : spending) {
                    pdf.bar(category.category().name(), report.amount(category.spentMinor(), currency),
                            category.spentMinor(), highest, colors.category(category.category().colorIndex()));
                }
            }

            pdf.heading(report.t("Transactions"));
            if (report.transactions().isEmpty()) {
                pdf.row(report.t("No transactions in {0}.", report.period()));
            }
            for (var transaction : report.transactions()) {
                pdf.entry(report.t("{0} · {1}", report.date(transaction.occurredOn()), report.category(transaction)),
                        report.amount(transaction.amountMinor(), SupportedCurrency.valueOf(transaction.currency())),
                        transaction.note());
            }
        }
    }
}
