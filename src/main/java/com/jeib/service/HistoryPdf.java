package com.jeib.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
    private final List<Long> offsets = new ArrayList<>(List.of(0L, 0L, 0L));
    private final List<Integer> pageIds = new ArrayList<>();
    private final List<AccessibleLine> pageText = new ArrayList<>();
    private long offset;
    private BufferedImage page;
    private Graphics2D graphics;
    private float y;

    private record AccessibleLine(String text, float x, float baseline) { }

    HistoryPdf(OutputStream output, boolean rightToLeft) throws IOException {
        this.output = output;
        this.rightToLeft = rightToLeft;
        ascii("%PDF-1.4\n");
        newPage();
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
        String displayed = text.isBlank() ? " " : text;
        var attributed = new AttributedString(displayed);
        attributed.addAttribute(TextAttribute.FONT, font);
        attributed.addAttribute(TextAttribute.RUN_DIRECTION,
                rightToLeft ? TextAttribute.RUN_DIRECTION_RTL : TextAttribute.RUN_DIRECTION_LTR);
        var measurer = new LineBreakMeasurer(attributed.getIterator(), graphics.getFontRenderContext());
        while (measurer.getPosition() < attributed.getIterator().getEndIndex()) {
            int first = measurer.getPosition();
            var line = measurer.nextLayout(WIDTH - 2 * MARGIN);
            float height = line.getAscent() + line.getDescent() + line.getLeading() + 3;
            if (y + height > HEIGHT - MARGIN) {
                nextPage();
            }
            float x = rightToLeft ? WIDTH - MARGIN - line.getAdvance() : MARGIN;
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
        page = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
        graphics = page.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.setColor(Color.BLACK);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        y = MARGIN;
        pageText.clear();
    }

    private void writePage() throws IOException {
        graphics.dispose();
        byte[] pixels = ((DataBufferByte) page.getRaster().getDataBuffer()).getData();
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
        stream("/Type /XObject /Subtype /Image /Filter /FlateDecode /ColorSpace /DeviceGray /BitsPerComponent 8 /Width "
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
