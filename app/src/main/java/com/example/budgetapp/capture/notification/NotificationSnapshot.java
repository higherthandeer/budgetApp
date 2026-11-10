package com.example.budgetapp.capture.notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable evidence for observation only. Notification post time is not trade time. */
public final class NotificationSnapshot {
    public final String packageName;
    public final String notificationKey;
    public final long postTimeMs;
    public final long capturedAtMs;
    public final String channelId;
    public final String title;
    public final String text;
    public final String bigText;
    public final List<String> textLines;
    public final boolean groupSummary;
    public final boolean truncated;
    public final String appVersion;

    public NotificationSnapshot(String packageName, String notificationKey, long postTimeMs,
            long capturedAtMs, String channelId, String title, String text, String bigText,
            List<String> textLines, boolean groupSummary, boolean truncated, String appVersion) {
        this.packageName = normalize(packageName);
        this.notificationKey = normalize(notificationKey);
        this.postTimeMs = postTimeMs;
        this.capturedAtMs = capturedAtMs;
        this.channelId = normalize(channelId);
        this.groupSummary = groupSummary;
        this.appVersion = normalize(appVersion);
        TextBudget budget = new TextBudget();
        this.title = budget.take(title);
        this.text = budget.take(text);
        this.bigText = budget.take(bigText);
        List<String> copy = new ArrayList<>();
        if (textLines != null) {
            for (int i=0; i<Math.min(32,textLines.size()); i++) copy.add(budget.take(textLines.get(i)));
        }
        this.textLines = Collections.unmodifiableList(copy);
        this.truncated = truncated || budget.truncated || (textLines != null && textLines.size()>32);
    }

    private static String normalize(String value) { return value == null ? "" : value; }

    private static final class TextBudget {
        private int remaining = 16384;
        private boolean truncated;

        String take(String value) {
            value = normalize(value);
            if (value.length() <= remaining) {
                remaining -= value.length();
                return value;
            }
            int end = remaining;
            if (end>0 && Character.isHighSurrogate(value.charAt(end-1))) end--;
            remaining -= end;
            truncated = true;
            return value.substring(0,end);
        }
    }
}
