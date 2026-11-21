package com.jeib.service;

import static com.jeib.i18n.Messages.t;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Draft links for feedback and crash reports. */
public final class ProblemReport {

    /** Keep the mailto link short for mail handlers with limited command-line lengths. */
    static final int MAX_URL = 1_800;

    private ProblemReport() {
    }

    /** An email draft with the system details and as many recent crash log lines as fit. */
    public static String problemUrl(List<String> logTail) {
        StringBuilder body = new StringBuilder()
                .append(t("What happened?")).append("\r\n\r\n\r\n")
                .append(t("What did you expect?")).append("\r\n\r\n\r\n")
                .append(t("System details")).append(": Jeib ").append(AppInfo.VERSION)
                .append(" · Java ").append(System.getProperty("java.version"))
                .append(" · ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
                .append("\r\n");
        String base = "mailto:" + AppInfo.SUPPORT_EMAIL + "?subject=" + encode(t("Jeib problem report")) + "&body=";
        String heading = "\r\n" + t("Recent crash log lines") + ":\r\n";
        int length = base.length() + encode(body + heading).length();
        int first = logTail.size();
        while (first > 0) {
            int nextLength = length + encode(logTail.get(first - 1) + "\r\n").length();
            if (nextLength > MAX_URL) {
                break;
            }
            length = nextLength;
            first--;
        }
        if (first < logTail.size()) {
            body.append(heading).append(String.join("\r\n", logTail.subList(first, logTail.size()))).append("\r\n");
        }
        return base + encode(body.toString());
    }

    /** Opens a Gmail compose window with the feedback address filled in. */
    public static String supportUrl() {
        return "https://mail.google.com/mail/u/0/?fs=1&to=" + AppInfo.FEEDBACK_EMAIL + "&tf=cm";
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
