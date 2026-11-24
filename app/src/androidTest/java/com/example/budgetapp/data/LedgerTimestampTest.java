package com.example.budgetapp.data;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.capture.notification.NotificationSnapshot;
import com.example.budgetapp.data.entity.SourceEventEntity;
import com.example.budgetapp.parsing.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.stats.StatsRepository;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LedgerTimestampTest {
    private LedgerDatabase db;
    private NotificationParser parser;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T08:00:00Z"), ZoneOffset.UTC);

    @Before public void setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LedgerDatabase.class)
                .addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();
        parser = new NotificationParser(TemplateCatalog.load(ApplicationProvider.getApplicationContext()));
    }

    @After public void close() { db.close(); }

    private NotificationSnapshot notification(boolean wechat, long posted, long captured) {
        return new NotificationSnapshot(wechat ? "com.tencent.mm" : "com.eg.android.AlipayGphone",
                "timestamp-test", posted, captured, "channel", wechat ? "微信支付" : "交易提醒",
                wechat ? "已支付¥12.34" : "你有一笔12.34元的支出。", "",
                Collections.emptyList(), false, false, "");
    }

    @Test public void delayedNotificationsKeepPostTimeAcrossShanghaiMidnightAndReplay() {
        long posted = Instant.parse("2026-11-20T15:59:58.321Z").toEpochMilli();
        for (boolean wechat : new boolean[]{true, false}) {
            var snapshot = notification(wechat, posted, clock.millis());
            var parsed = parser.parse(snapshot);
            assertEquals(ParseResult.Disposition.CANDIDATE, parsed.disposition);
            assertEquals(posted, parsed.candidate.occurredAtMs.longValue());
            assertEquals(TimePrecision.SECOND, parsed.candidate.timePrecision);
            assertEquals("NOTIFICATION_POST_TIME", parsed.candidate.dateBasis.name());
            assertEquals(posted, EventCodec.decodeParsed(EventCodec.encodeParsed(parsed)).candidate.occurredAtMs.longValue());
            SourceEventEntity event = new SourceEventEntity();
            event.id = "timestamp-" + wechat;
            event.eventKey = event.id;
            event.sourceType = "NOTIFICATION";
            event.provider = wechat ? "WECHAT" : "ALIPAY";
            event.accountScopeId = "timestamp-scope";
            event.capturedAtMs = clock.millis();
            event.rawExpiresAtMs = clock.millis() + 86400000L;
            event.rawPayload = EventCodec.encodeSnapshot(snapshot);
            event.processState = "NEW";
            db.sourceEventDao().insertIgnore(event);
            var processor = new TransactionProcessor(db, parser, new CategoryMatcher(), clock);
            var result = processor.process(event.id);
            assertEquals(TransactionProcessor.ProcessResult.Status.POSTED, result.status);
            var transaction = db.ledgerDao().find(result.transactionId);
            assertEquals(posted, transaction.occurredAtMs);
            assertEquals("SECOND", transaction.timePrecision);
            assertFalse(transaction.note.contains("接收日"));
            assertEquals(TransactionProcessor.ProcessResult.Status.ALREADY_HANDLED, processor.process(event.id).status);
        }
        assertEquals(2, db.ledgerDao().countPosted());
    }

    @Test public void missingPostTimeFallsBackToFullCaptureTime() {
        long captured = Instant.parse("2026-11-21T16:00:07.654Z").toEpochMilli();
        for (boolean wechat : new boolean[]{true, false}) {
            for (long missing : new long[]{0, -1}) {
                var parsed = parser.parse(notification(wechat, missing, captured));
                assertEquals(ParseResult.Disposition.CANDIDATE, parsed.disposition);
                assertEquals(captured, parsed.candidate.occurredAtMs.longValue());
                assertEquals(TimePrecision.SECOND, parsed.candidate.timePrecision);
                assertEquals("NOTIFICATION_CAPTURE_TIME", parsed.candidate.dateBasis.name());
            }
            assertEquals(ParseResult.Disposition.REVIEW, parser.parse(notification(wechat, 0, 0)).disposition);
        }
    }

    @Test public void ledgerAndStatisticsSortByOccurrenceThenCreationDescending() {
        long at = Instant.parse("2026-11-21T02:03:04Z").toEpochMilli();
        // IDs deliberately oppose creation order, so UUID ordering cannot hide a regression.
        insert("a-earlier-creation", at, 10);
        insert("z-later-creation", at, 20);
        insert("oldest", at - 1000, 30);
        insert("newest", at + 1000, 1);
        var filter = new LedgerFilter(Long.MIN_VALUE, Long.MAX_VALUE, LedgerFilter.Metric.NET_OUTFLOW, null, null);
        List<String> expected = Arrays.asList("newest", "z-later-creation", "a-earlier-creation", "oldest");
        List<String> actual = new ArrayList<>();
        for (var tx : new ManualLedgerRepository(db, clock).list(filter)) actual.add(tx.id);
        assertEquals(expected, actual);
        actual.clear();
        for (var entry : new StatsRepository(db, Runnable::run).load(filter).entries) actual.add(entry.transaction.id);
        assertEquals(expected, actual);
    }

    private void insert(String id, long at, long created) {
        var tx = new com.example.budgetapp.data.entity.LedgerTransactionEntity();
        tx.id = id;
        tx.kind = "EXPENSE";
        tx.amountMinor = 100;
        tx.currency = "CNY";
        tx.occurredAtMs = at;
        tx.timePrecision = "SECOND";
        tx.sourceZoneId = "Asia/Shanghai";
        tx.merchantRaw = "排序店";
        tx.provider = "OTHER";
        tx.accountScopeId = "scope";
        tx.categoryId = LedgerTypes.UNCATEGORIZED_EXPENSE;
        tx.categoryOrigin = "DEFAULT";
        tx.bookingState = "POSTED";
        tx.userLockedFields = "[]";
        tx.revision = 1;
        tx.createdAt = created;
        tx.updatedAt = created;
        db.ledgerDao().insert(tx);
    }
}
