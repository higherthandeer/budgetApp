package com.example.budgetapp.capture.notification;

import static org.junit.Assert.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class DiagnosticBufferTest {
    private final MutableClock clock = new MutableClock();

    @Test public void collectionRequiresExplicitConsent() {
        DiagnosticBuffer buffer = new DiagnosticBuffer(true, clock);
        buffer.accept(sample("com.tencent.mm", "1"));
        assertFalse(buffer.isOpen());
        assertTrue(buffer.latest().isEmpty());
        buffer.openWindow();
        buffer.accept(sample("com.tencent.mm", "2"));
        assertEquals(1, buffer.latest().size());
    }

    @Test public void releaseCannotOpenOrCollect() {
        DiagnosticBuffer buffer = new DiagnosticBuffer(false, clock);
        buffer.openWindow();
        buffer.accept(sample("com.tencent.mm", "1"));
        assertFalse(buffer.isOpen());
        assertTrue(buffer.latest().isEmpty());
    }

    @Test public void otherPackagesAreNeverRetained() {
        DiagnosticBuffer buffer = new DiagnosticBuffer(true, clock);
        buffer.openWindow();
        buffer.accept(sample("com.example.chat", "1"));
        buffer.accept(sample("com.eg.android.AlipayGphone", "2"));
        assertEquals(1, buffer.latest().size());
        assertEquals("2", buffer.latest().get(0).notificationKey);
    }

    @Test public void exactlyFifteenMinutesExpiresAndClearsMemory() {
        DiagnosticBuffer buffer = new DiagnosticBuffer(true, clock);
        buffer.openWindow();
        buffer.accept(sample("com.tencent.mm", "1"));
        clock.advance(899999);
        assertTrue(buffer.isOpen());
        assertEquals(1L, buffer.remainingMillis());
        clock.advance(1);
        assertFalse(buffer.isOpen());
        assertTrue(buffer.latest().isEmpty());
        assertEquals(0L, buffer.remainingMillis());
    }

    @Test public void closingAndReopeningDoNotRevealOldSamples() {
        DiagnosticBuffer buffer = new DiagnosticBuffer(true, clock);
        buffer.openWindow();
        buffer.accept(sample("com.tencent.mm", "old"));
        List<NotificationSnapshot> view = buffer.latest();
        assertThrows(UnsupportedOperationException.class, () -> view.clear());
        buffer.closeWindow();
        assertTrue(buffer.latest().isEmpty());
        buffer.openWindow();
        assertTrue(buffer.latest().isEmpty());
    }

    @Test public void historyIsBoundedToOneHundredLatestSamples() {
        DiagnosticBuffer buffer = new DiagnosticBuffer(true, clock);
        buffer.openWindow();
        for (int i=0; i<105; i++) buffer.accept(sample("com.tencent.mm", String.valueOf(i)));
        assertEquals(100, buffer.latest().size());
        assertEquals("5", buffer.latest().get(0).notificationKey);
        assertEquals("104", buffer.latest().get(99).notificationKey);
    }

    @Test public void backwardsClockClosesInsteadOfExtendingConsent() {
        DiagnosticBuffer buffer = new DiagnosticBuffer(true, clock);
        buffer.openWindow();
        buffer.accept(sample("com.tencent.mm", "old"));
        clock.advance(-1);
        assertFalse(buffer.isOpen());
        assertTrue(buffer.latest().isEmpty());
    }

    private static NotificationSnapshot sample(String pkg, String key) {
        return new NotificationSnapshot(pkg,key,1L,2L,"payments","title","text","",
                Collections.emptyList(),false,false,"1.0");
    }
    static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-11-10T04:00:00Z");
        void advance(long ms) { instant = instant.plusMillis(ms); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
