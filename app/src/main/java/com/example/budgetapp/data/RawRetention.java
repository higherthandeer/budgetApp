package com.example.budgetapp.data;

import com.example.budgetapp.data.entity.SourceEventEntity;

import java.time.Clock;
import java.util.*;
import java.util.function.IntSupplier;

public final class RawRetention {
    public static final class RawItem {
        public final String eventId, provider;
        public final long capturedAtMs, expiresAtMs;

        RawItem(SourceEventEntity e, long expiry) {
            eventId = e.id;
            provider = e.provider;
            capturedAtMs = e.capturedAtMs;
            expiresAtMs = expiry;
        }
    }

    private final LedgerDatabase db;
    private final IntSupplier days;
    private final Clock clock;

    public RawRetention(LedgerDatabase db, LocalSettings settings, Clock clock) {
        this(db, settings::retentionDays, clock);
    }

    public RawRetention(LedgerDatabase db, Clock clock) {
        this(db, () -> 7, clock);
    }

    private RawRetention(LedgerDatabase db, IntSupplier days, Clock clock) {
        this.db = db;
        this.days = days;
        this.clock = clock;
    }

    public static long expiryAt(long capturedAtMs, int days) {
        if (days != 7 && days != 30)
            throw new IllegalArgumentException("Retention must be 7 or 30 days");
        try {
            return Math.addExact(capturedAtMs, days * 86400000L);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private long expiry(SourceEventEntity e) {
        return e.rawExpiresAtMs == null ? expiryAt(e.capturedAtMs, days.getAsInt()) : e.rawExpiresAtMs;
    }

    public boolean isExpired(SourceEventEntity e) {
        return clock.millis() >= expiry(e);
    }

    public String readRaw(String eventId) {
        SourceEventEntity e = db.sourceEventDao().find(eventId);
        return e == null ? null : readRaw(e);
    }

    public String readRaw(SourceEventEntity e) {
        return isExpired(e) ? null : e.rawPayload;
    }

    public int purgeExpired() {
        return db.runInTransaction(() -> {
            int count = 0;
            long now = clock.millis();
            for (SourceEventEntity e : db.sourceEventDao().rawEvents()) {
                long expiry = expiry(e);
                boolean stamp = e.rawExpiresAtMs == null;
                if (stamp) e.rawExpiresAtMs = expiry;
                if (now >= expiry) {
                    e.rawPayload = null;
                    count++;
                }
                if (stamp || e.rawPayload == null) db.sourceEventDao().update(e);
            }
            return count;
        });
    }

    public void extendEvent(String eventId, int days) {
        expiryAt(0, days);
        db.runInTransaction(() -> {
            SourceEventEntity e = required(eventId);
            if (e.rawPayload == null || isExpired(e))
                throw new IllegalArgumentException("Raw content is no longer available");
            e.rawExpiresAtMs = expiryAt(e.capturedAtMs, days);
            if (isExpired(e)) e.rawPayload = null;
            db.sourceEventDao().update(e);
        });
    }

    public void clearRaw(String eventId) {
        db.runInTransaction(() -> {
            SourceEventEntity e = required(eventId);
            e.rawPayload = null;
            db.sourceEventDao().update(e);
        });
    }

    public List<RawItem> listAvailable() {
        List<RawItem> items = new ArrayList<>();
        for (SourceEventEntity e : db.sourceEventDao().rawEvents())
            if (!isExpired(e)) items.add(new RawItem(e, expiry(e)));
        return Collections.unmodifiableList(items);
    }

    private SourceEventEntity required(String id) {
        SourceEventEntity e = db.sourceEventDao().find(id);
        if (e == null) throw new IllegalArgumentException("Missing event");
        return e;
    }
}
