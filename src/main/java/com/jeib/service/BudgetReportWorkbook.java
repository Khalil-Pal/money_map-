package com.jeib.service;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.ReadingOrder;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.XDDFColor;
import org.apache.poi.xddf.usermodel.XDDFShapeProperties;
import org.apache.poi.xddf.usermodel.XDDFSolidFillProperties;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.BarGrouping;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STCellType;

final class BudgetReportWorkbook {
    private static final int FIRST_SPENDING_ROW = 13;
    private static final int CHARTED_CATEGORIES = 12;

    private final XSSFWorkbook workbook;
    private final ReportColors colors;
    private final boolean rightToLeft;
    private final XSSFCellStyle title;
    private final XSSFCellStyle header;
    private final XSSFCellStyle text;
    private final XSSFCellStyle number;
    private final XSSFCellStyle date;

    private BudgetReportWorkbook(XSSFWorkbook workbook, ReportColors colors, boolean rightToLeft) {
        this.workbook = workbook;
        this.colors = colors;
        this.rightToLeft = rightToLeft;
        title = style(20, true, null);
        header = style(11, true, colors.get("bg-surface-alt"));
        text = style(11, false, null);
        text.setWrapText(true);
        number = style(11, false, null);
        number.setReadingOrder(ReadingOrder.LEFT_TO_RIGHT);
        number.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00;[Red]-#,##0.00;0.00"));
        date = style(11, false, null);
        date.setReadingOrder(ReadingOrder.LEFT_TO_RIGHT);
        date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
    }

    static void write(BudgetReportService.Report report, OutputStream output) throws IOException {
        try (var workbook = new XSSFWorkbook()) {
            var writer = new BudgetReportWorkbook(workbook, new ReportColors(), report.language().rightToLeft());
            writer.summary(report);
            writer.ledger(report);
            workbook.write(output);
        }
    }

    private void summary(BudgetReportService.Report report) {
        var sheet = sheet(report.t("Budget report"));
        sheet.setColumnWidth(0, 35 * 256);
        sheet.setColumnWidth(1, 18 * 256);
        sheet.setColumnWidth(2, 3 * 256);
        sheet.setZoom(90);

        var nameRow = sheet.createRow(0);
        nameRow.setHeightInPoints(38);
        put(nameRow, 0, report.plan().name(), title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));
        var periodRow = sheet.createRow(1);
        put(periodRow, 0, report.period(), text);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 7));

        headings(sheet.createRow(3), report.t("Budget vs. spending"), report.plan().currency().name());
        var totals = report.summary();
        metric(sheet, 4, report.t("Budget limit"), report.plan().limitMinor());
        metric(sheet, 5, report.t("Total spent"), totals.spentMinor());
        metric(sheet, 6, report.t("Remaining"), totals.remainingMinor());
        var status = switch (totals.status()) {
            case OK -> report.t("Within limit");
            case WARNING -> report.t("Near limit");
            case EXCEEDED -> report.t("Limit exceeded");
            case NO_LIMIT -> report.t("No limit");
        };
        var statusStyle = switch (totals.status()) {
            case OK -> style(11, true, colors.get("success-tint"));
            case WARNING -> style(11, true, colors.get("warning-tint"));
            case EXCEEDED -> style(11, true, colors.get("danger-tint"));
            case NO_LIMIT -> header;
        };
        var statusRow = sheet.createRow(7);
        put(statusRow, 0, report.t("Status"), text);
        put(statusRow, 1, status, statusStyle);

        var rateNote = sheet.createRow(9);
        rateNote.setHeightInPoints(32);
        put(rateNote, 0, report.t("Totals use the exchange rates saved at export."), text);
        sheet.addMergedRegion(new CellRangeAddress(9, 9, 0, 1));
        if (!totals.missingRates().isEmpty()) {
            var warning = sheet.createRow(10);
            warning.setHeightInPoints(44);
            put(warning, 0, report.t("Some spending is missing exchange rates: {0}.", report.missingRates()), text);
            sheet.addMergedRegion(new CellRangeAddress(10, 10, 0, 1));
        }

        String categoryTitle = report.t("Spending by category");
        headings(sheet.createRow(FIRST_SPENDING_ROW - 1), categoryTitle, report.plan().currency().name());
        var spending = report.spending();
        if (spending.isEmpty()) {
            put(sheet.createRow(FIRST_SPENDING_ROW), 0, report.t("No spending to chart."), text);
        }
        int row = FIRST_SPENDING_ROW;
        for (var category : spending) {
            metric(sheet, row++, category.category().name(), category.spentMinor());
        }
        chart(sheet, report.t("Budget vs. spending"), 4, 5, 3, 13,
                List.of(colors.get("border-strong"), colors.get("primary")), report.plan().currency().name());
        if (!spending.isEmpty()) {
            var charted = spending.stream().limit(CHARTED_CATEGORIES).toList();
            chart(sheet, charted.size() < spending.size() ? report.t("Top spending categories") : categoryTitle,
                    FIRST_SPENDING_ROW, FIRST_SPENDING_ROW + charted.size() - 1, 14,
                    14 + Math.max(11, charted.size() * 2),
                    charted.stream().map(category -> colors.category(category.category().colorIndex())).toList(),
                    report.plan().currency().name());
        }
    }

    private void ledger(BudgetReportService.Report report) {
        var sheet = sheet(report.t("Transactions"));
        sheet.setColumnWidth(0, 15 * 256);
        sheet.setColumnWidth(1, 27 * 256);
        sheet.setColumnWidth(2, 12 * 256);
        sheet.setColumnWidth(3, 15 * 256);
        sheet.setColumnWidth(4, 54 * 256);
        headings(sheet.createRow(0), report.t("Date"), report.t("Category"), report.t("Currency"),
                report.t("Amount"), report.t("Note"));
        sheet.createFreezePane(0, 1);
        int index = 1;
        for (var transaction : report.transactions()) {
            var row = sheet.createRow(index++);
            var transactionDate = row.createCell(0);
            transactionDate.setCellValue(transaction.occurredOn());
            transactionDate.setCellStyle(date);
            String category = report.category(transaction);
            put(row, 1, category, text);
            put(row, 2, transaction.currency(), text);
            money(row, 3, transaction.amountMinor());
            String note = transaction.note() == null ? "" : transaction.note();
            put(row, 4, note, text);
            int noteLines = note.lines().mapToInt(line -> Math.max(1, (line.length() + 47) / 48)).sum();
            int categoryLines = Math.max(1, (category.length() + 22) / 23);
            row.setHeightInPoints(Math.min(400, 5 + 16 * Math.max(categoryLines, noteLines)));
        }
        if (index == 1) {
            put(sheet.createRow(1), 0, report.t("No transactions in {0}.", report.period()), text);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 4));
        } else {
            sheet.setAutoFilter(new CellRangeAddress(0, index - 1, 0, 4));
        }
    }

    private XSSFSheet sheet(String name) {
        var sheet = workbook.createSheet(name);
        sheet.setRightToLeft(rightToLeft);
        sheet.setDisplayGridlines(false);
        return sheet;
    }

    private void chart(XSSFSheet sheet, String chartTitle, int first, int last, int top, int bottom,
                       List<Color> fills, String currency) {
        var drawing = sheet.createDrawingPatriarch();
        var anchor = drawing.createAnchor(0, 0, 0, 0, 3, top, 12, bottom);
        var chart = drawing.createChart(anchor);
        chart.setTitleText(chartTitle);
        chart.setTitleOverlay(false);
        var labels = chart.createCategoryAxis(AxisPosition.LEFT);
        var values = chart.createValueAxis(AxisPosition.BOTTOM);
        values.setMinimum(0);
        values.setNumberFormat("#,##0.00");
        values.setTitle(currency);
        var data = (XDDFBarChartData) chart.createData(ChartTypes.BAR, labels, values);
        data.setBarDirection(BarDirection.BAR);
        data.setBarGrouping(BarGrouping.CLUSTERED);
        data.setGapWidth(65);
        var series = data.addSeries(
                XDDFDataSourcesFactory.fromStringCellRange(sheet, new CellRangeAddress(first, last, 0, 0)),
                XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(first, last, 1, 1)));
        series.setTitle(chartTitle);
        var seriesStyle = new XDDFShapeProperties();
        seriesStyle.setFillProperties(chartFill(colors.get("primary")));
        series.setShapeProperties(seriesStyle);
        for (int index = 0; index < fills.size(); index++) {
            series.getDataPoint(index).setFillProperties(chartFill(fills.get(index)));
        }
        chart.plot(data);
    }

    private void metric(XSSFSheet sheet, int index, String label, long minor) {
        var row = sheet.createRow(index);
        row.setHeightInPoints(30);
        put(row, 0, label, text);
        money(row, 1, minor);
    }

    private void money(XSSFRow row, int index, long minor) {
        var cell = row.createCell(index);
        cell.setCellStyle(number);
        cell.getCTCell().setT(STCellType.N);
        cell.getCTCell().setV(BigDecimal.valueOf(minor, 2).toPlainString());
    }

    private void headings(XSSFRow row, String... labels) {
        row.setHeightInPoints(30);
        for (int index = 0; index < labels.length; index++) {
            put(row, index, labels[index], header);
        }
    }

    private static void put(XSSFRow row, int index, String value, XSSFCellStyle style) {
        var cell = row.createCell(index);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private XSSFCellStyle style(int size, boolean bold, Color background) {
        var style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setIndention((short) 1);
        if (rightToLeft) {
            style.setReadingOrder(ReadingOrder.RIGHT_TO_LEFT);
            style.setAlignment(HorizontalAlignment.RIGHT);
        }
        var font = workbook.createFont();
        font.setFontName("Segoe UI");
        font.setFontHeightInPoints((short) size);
        font.setBold(bold);
        font.setColor(excelColor(colors.get("text-primary")));
        style.setFont(font);
        if (background != null) {
            style.setFillForegroundColor(excelColor(background));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBottomBorderColor(excelColor(colors.get("border")));
        }
        return style;
    }

    private static XSSFColor excelColor(Color color) {
        return new XSSFColor(rgb(color));
    }

    private static XDDFSolidFillProperties chartFill(Color color) {
        return new XDDFSolidFillProperties(XDDFColor.from(rgb(color)));
    }

    private static byte[] rgb(Color color) {
        return new byte[] {(byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue()};
    }
}
