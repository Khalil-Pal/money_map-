package com.jeib.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.zip.DeflaterOutputStream;

/** Writes paginated grayscale pages. Java2D handles Unicode shaping and bidirectional text. */
final class HistoryPdf implements AutoCloseable {

    private static final int WIDTH = 1190;
    private static final int HEIGHT = 1684;
    private static final int MARGIN = 96;
    private static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 23);
    private static final Font HEADING = BODY.deriveFont(Font.BOLD, 32);
    private final OutputStream output;
    private final boolean rightToLeft;
    private final ReportColors colors;
    private final List<Long> offsets = new ArrayList<>(List.of(0L, 0L, 0L));
    private final List<Integer> pageIds = new ArrayList<>();
    private final List<AccessibleLine> pageText = new ArrayList<>();
    private long offset;
    private BufferedImage page;
    private Graphics2D graphics;
    private float y;

    private record AccessibleLine(String text, float x, float baseline) { }

    HistoryPdf(OutputStream output, boolean rightToLeft) throws IOException {
        this(output, rightToLeft, null);
    }

    HistoryPdf(OutputStream output, boolean rightToLeft, ReportColors colors) throws IOException {
        this.output = output;
        this.rightToLeft = rightToLeft;
        this.colors = colors;
        ascii("%PDF-1.4\n");
        newPage();
    }

    void title(String text) throws IOException {
        paragraph(text, HEADING.deriveFont(50f));
        y += 10;
    }

    void pair(String label, String amount) throws IOException {
        if (y > HEIGHT - MARGIN - 92) {
            nextPage();
        }
        int amountWidth = 270;
        int labelWidth = WIDTH - 2 * MARGIN - amountWidth - 20;
        float top = y;
        paragraph(amount, BODY, rightToLeft ? MARGIN : WIDTH - MARGIN - amountWidth,
                amountWidth, false, !rightToLeft);
        float amountBottom = y;
        y = top;
        paragraph(label, BODY, rightToLeft ? WIDTH - MARGIN - labelWidth : MARGIN,
                labelWidth, rightToLeft, rightToLeft);
        y = Math.max(y, amountBottom);
    }

    void entry(String label, String amount, String note) throws IOException {
        pair(label, amount);
        if (note != null && !note.isBlank()) {
            graphics.setColor(colors.get("text-secondary"));
            for (String line : note.split("\\R")) {
                paragraph(line, BODY.deriveFont(20f));
            }
        }
        y += 6;
        graphics.setColor(colors.get("border"));
        graphics.drawLine(MARGIN, (int) y, WIDTH - MARGIN, (int) y);
        graphics.setColor(colors.get("text-primary"));
        y += 10;
    }

    void bar(String label, String amount, long value, long maximum, Color color) throws IOException {
        pair(label, amount);
        float track = WIDTH - 2 * MARGIN;
        float fill = track * ((float) value / maximum);
        graphics.setColor(colors.get("bg-surface-alt"));
        graphics.fill(new RoundRectangle2D.Float(MARGIN, y + 4, track, 13, 10, 10));
        if (value > 0) {
            graphics.setColor(color);
            graphics.fill(new RoundRectangle2D.Float(rightToLeft ? WIDTH - MARGIN - fill : MARGIN,
                    y + 4, Math.max(2, fill), 13, 10, 10));
        }
        graphics.setColor(colors.get("text-primary"));
        y += 30;
    }

    void heading(String text) throws IOException {
        if (y > HEIGHT - MARGIN - 105) {
            nextPage();
        }
        y += 18;
        paragraph(text, HEADING);
    }

    void row(String text) throws IOException {
        for (String line : text.split("\\R", -1)) {
            paragraph(line, BODY);
        }
        y += 7;
    }

    private void paragraph(String text, Font font) throws IOException {
        paragraph(text, font, MARGIN, WIDTH - 2 * MARGIN, rightToLeft, rightToLeft);
    }

    private void paragraph(String text, Font font, int start, int width, boolean rtl, boolean alignRight)
            throws IOException {
        String displayed = text.isBlank() ? " " : text;
        var attributed = new AttributedString(displayed);
        attributed.addAttribute(TextAttribute.FONT, font);
        attributed.addAttribute(TextAttribute.RUN_DIRECTION,
                rtl ? TextAttribute.RUN_DIRECTION_RTL : TextAttribute.RUN_DIRECTION_LTR);
        var measurer = new LineBreakMeasurer(attributed.getIterator(), graphics.getFontRenderContext());
        while (measurer.getPosition() < attributed.getIterator().getEndIndex()) {
            int first = measurer.getPosition();
            var line = measurer.nextLayout(width);
            float height = line.getAscent() + line.getDescent() + line.getLeading() + 3;
            if (y + height > HEIGHT - MARGIN) {
                Color color = graphics.getColor();
                nextPage();
                graphics.setColor(color);
            }
            float x = alignRight ? start + width - line.getAdvance() : start;
            float baseline = y + line.getAscent();
            line.draw(graphics, x, baseline);
            pageText.add(new AccessibleLine(displayed.substring(first, measurer.getPosition()), x, baseline));
            y += height;
        }
    }

    private void nextPage() throws IOException {
        writePage();
        newPage();
    }

    private void newPage() {
        page = new BufferedImage(WIDTH, HEIGHT, colors == null
                ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR);
        graphics = page.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.setColor(colors == null ? Color.BLACK : colors.get("text-primary"));
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        y = MARGIN;
        pageText.clear();
    }

    private void writePage() throws IOException {
        graphics.dispose();
        byte[] pixels = ((DataBufferByte) page.getRaster().getDataBuffer()).getData();
        if (colors != null) {
            for (int i = 0; i < pixels.length; i += 3) {
                byte blue = pixels[i];
                pixels[i] = pixels[i + 2];
                pixels[i + 2] = blue;
            }
        }
        var compressed = new ByteArrayOutputStream();
        try (var deflater = new DeflaterOutputStream(compressed)) {
            deflater.write(pixels);
        }
        int pageId = offsets.size();
        pageIds.add(pageId);
        int imageId = pageId + 1;
        int contentId = pageId + 2;
        object(pageId);
        ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << "
                + "/Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> /XObject << /Im "
                + imageId + " 0 R >> >> /Contents " + contentId + " 0 R >>\nendobj\n");
        object(imageId);
        stream("/Type /XObject /Subtype /Image /Filter /FlateDecode /ColorSpace "
                + (colors == null ? "/DeviceGray" : "/DeviceRGB") + " /BitsPerComponent 8 /Width "
                + WIDTH + " /Height " + HEIGHT, compressed.toByteArray());
        object(contentId);
        var content = new StringBuilder("q 595 0 0 842 0 0 cm /Im Do Q\n");
        for (var line : pageText) {
            // The image carries the shaped glyphs. ActualText supplies their Unicode text for copying and search.
            String unicode = HexFormat.of().formatHex(line.text().getBytes(StandardCharsets.UTF_16BE));
            content.append("/Span << /ActualText <FEFF").append(unicode).append("> >> BDC\n")
                    .append("BT /F1 10 Tf 3 Tr ").append(line.x() / 2).append(' ')
                    .append((HEIGHT - line.baseline()) / 2).append(" Td (x) Tj ET\nEMC\n");
        }
        stream("", content.toString().getBytes(StandardCharsets.US_ASCII));
    }

    private void object(int id) throws IOException {
        if (id == offsets.size()) {
            offsets.add(offset);
        } else {
            offsets.set(id, offset);
        }
        ascii(id + " 0 obj\n");
    }

    private void stream(String properties, byte[] content) throws IOException {
        ascii("<< " + properties + " /Length " + content.length + " >>\nstream\n");
        output.write(content);
        offset += content.length;
        ascii("\nendstream\nendobj\n");
    }

    private void ascii(String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        output.write(bytes);
        offset += bytes.length;
    }

    @Override
    public void close() throws IOException {
        writePage();
        object(1);
        ascii("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        object(2);
        var kids = new StringBuilder();
        for (int id : pageIds) {
            kids.append(id).append(" 0 R ");
        }
        ascii("<< /Type /Pages /Count " + pageIds.size() + " /Kids [" + kids + "] >>\nendobj\n");
        long xref = offset;
        ascii("xref\n0 " + offsets.size() + "\n0000000000 65535 f \n");
        for (long position : offsets.subList(1, offsets.size())) {
            ascii(String.format(Locale.ROOT, "%010d 00000 n \n", position));
        }
        ascii("trailer\n<< /Size " + offsets.size() + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
    }
}
