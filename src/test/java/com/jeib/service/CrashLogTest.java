package com.jeib.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrashLogTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void anErrorIsWrittenWithItsContextAndTheListenerHearsIt() throws Exception {
        CrashLog log = new CrashLog(tempDir, CLOCK);
        List<Throwable> heard = new ArrayList<>();
        log.onCrash(heard::add);

        IllegalStateException error = new IllegalStateException("boom");
        log.record("JavaFX Application Thread", error);

        String text = Files.readString(log.file());
        assertTrue(text.contains("2026-11-12T10:00:00Z"));
        assertTrue(text.contains("Jeib " + AppInfo.VERSION));
        assertTrue(text.contains("java.lang.IllegalStateException: boom"));
        assertTrue(text.contains("thread JavaFX Application Thread"));
        assertEquals(List.of(error), heard);
        assertTrue(log.lastLines(3).size() <= 3);
    }

    @Test
    void aBigLogIsRotatedSoItNeverGrowsForever() throws Exception {
        CrashLog log = new CrashLog(tempDir, CLOCK);
        Files.createDirectories(log.file().getParent());
        Files.writeString(log.file(), "x".repeat((int) CrashLog.MAX_BYTES + 10));

        log.record("main", new RuntimeException("after rotation"));

        assertTrue(Files.exists(log.file().resolveSibling("jeib.log.1")));
        assertTrue(Files.size(log.file()) < CrashLog.MAX_BYTES);
        assertTrue(Files.readString(log.file()).contains("after rotation"));
    }

    @Test
    void noLogMeansNoLines() {
        assertEquals(List.of(), new CrashLog(tempDir, CLOCK).lastLines(40));
    }

    @Test
    void problemReportsStayShortEnoughForABrowser() {
        List<String> hugeLog = new ArrayList<>(Collections.nCopies(2_000, "\tat com.jeib.Something.method(Something.java:42)"));
        hugeLog.add("the newest line");

        String url = ProblemReport.problemUrl(hugeLog);

        assertTrue(url.startsWith(AppInfo.ISSUES + "?title="));
        assertTrue(url.length() <= ProblemReport.MAX_URL, "url is " + url.length());
        String body = URLDecoder.decode(url.substring(url.indexOf("&body=") + 6), StandardCharsets.UTF_8);
        assertTrue(body.contains("the newest line")); // the oldest lines go first
        assertTrue(body.contains("Jeib " + AppInfo.VERSION));
        assertTrue(ProblemReport.feedbackUrl().contains("title=Idea"));
    }
}
