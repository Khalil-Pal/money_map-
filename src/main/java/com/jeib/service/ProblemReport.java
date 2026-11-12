package com.jeib.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Links that open a new GitHub issue, already filled in ("Report a problem" and "Send feedback",
 * features 37 and 17). Nothing is sent by Jeib itself: the browser shows the issue, and the user
 * reads, edits, and submits it (or not).
 */
public final class ProblemReport {

    /** Browsers and GitHub reject very long URLs; the log part is trimmed to stay well under. */
    static final int MAX_URL = 7_000;

    private ProblemReport() {
    }

    /** A bug report with the system details and the last lines of the crash log. */
    public static String problemUrl(List<String> logTail) {
        StringBuilder body = new StringBuilder()
                .append("**What happened?**\n\n\n**What did you expect?**\n\n\n")
                .append("**System:** Jeib ").append(AppInfo.VERSION)
                .append(" · Java ").append(System.getProperty("java.version"))
                .append(" · ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
                .append("\n");
        String base = AppInfo.ISSUES + "?title=" + encode("Problem: ") + "&body=";
        if (!logTail.isEmpty()) {
            String head = "\n**Crash log (last lines):**\n```\n";
            String tail = "\n```\n";
            StringBuilder log = new StringBuilder(String.join("\n", logTail));
            // Drop the oldest lines until the whole link fits.
            while (log.length() > 0 && (base + encode(body + head + log + tail)).length() > MAX_URL) {
                int cut = log.indexOf("\n");
                log.delete(0, cut < 0 ? log.length() : cut + 1);
            }
            if (log.length() > 0) {
                body.append(head).append(log).append(tail);
            }
        }
        return base + encode(body.toString());
    }

    /** An idea or other feedback. */
    public static String feedbackUrl() {
        return AppInfo.ISSUES + "?title=" + encode("Idea: ")
                + "&body=" + encode("**Your idea or feedback:**\n\n\n(Jeib " + AppInfo.VERSION + ")\n");
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
