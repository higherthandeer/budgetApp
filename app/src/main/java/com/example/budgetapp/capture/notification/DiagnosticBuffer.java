package com.example.budgetapp.capture.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** No disk, logs, or implicit consent. Runtime supplies a monotonic clock. */
public final class DiagnosticBuffer {
    private final boolean debugBuild;
    private final Clock clock;
    private final ArrayDeque<NotificationSnapshot> samples = new ArrayDeque<>();
    private Instant startedAt;
    private Instant expiresAt;

    public DiagnosticBuffer(boolean debugBuild, Clock clock) {
        this.debugBuild = debugBuild;
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized void openWindow() {
        closeWindow();
        if (!debugBuild) return;
        startedAt = clock.instant();
        expiresAt = startedAt.plusSeconds(15*60);
    }

    public synchronized void closeWindow() {
        samples.clear();
        startedAt = null;
        expiresAt = null;
    }

    public synchronized boolean isOpen() {
        if (expiresAt == null) return false;
        Instant now = clock.instant();
        if (now.isBefore(startedAt) || !now.isBefore(expiresAt)) closeWindow();
        return expiresAt != null;
    }

    public synchronized long remainingMillis() {
        return isOpen() ? Math.max(0,expiresAt.toEpochMilli()-clock.millis()) : 0;
    }

    public synchronized void accept(NotificationSnapshot snapshot) {
        if (!isOpen() || snapshot == null || !NotificationPackages.isSupported(snapshot.packageName)) return;
        if (samples.size() == 100) samples.removeFirst();
        samples.addLast(snapshot);
    }

    public synchronized List<NotificationSnapshot> latest() {
        if (!isOpen()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(samples));
    }
}
