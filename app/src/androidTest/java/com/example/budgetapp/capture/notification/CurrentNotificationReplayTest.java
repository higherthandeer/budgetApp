package com.example.budgetapp.capture.notification;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.data.*;
import com.example.budgetapp.parsing.*;
import com.example.budgetapp.processing.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class) public class CurrentNotificationReplayTest {
 private LedgerDatabase db;private ExecutorService io;private EventIntake intake;private CurrentNotificationReplay replay;private Clock clock;
 @Before public void setup(){var context=ApplicationProvider.getApplicationContext();clock=Clock.fixed(Instant.parse("2026-11-13T02:00:00Z"),ZoneOffset.UTC);db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase.class).addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();io=Executors.newSingleThreadExecutor();var parser=new NotificationParser(TemplateCatalog.load(context));var retention=new RawRetention(db,clock);intake=new EventIntake(db,parser,new LocalSettings(context),new com.example.budgetapp.worker.RecoveryScheduler(context),io);replay=new CurrentNotificationReplay(db,intake,new TransactionProcessor(db,parser,new CategoryMatcher(),clock,retention),parser,retention);}
 @After public void close(){io.shutdownNow();db.close();}
 private NotificationSnapshot snapshot(String key,long at){return new NotificationSnapshot("com.eg.android.AlipayGphone",key,12345,at,"alipay_default","交易提醒","你有一笔19.00元的支出，点此查看详情","",Collections.emptyList(),false,false,"12.12.12.8000");}
 @Test public void currentNoticeBooksAndRepeatedClickDoesNotDuplicate(){var s=snapshot("one",clock.millis());assertEquals(1,replay.process(Collections.singletonList(s)).posted);assertEquals(1,replay.process(Collections.singletonList(s)).unchanged);assertEquals(1,db.ledgerDao().countPosted());assertEquals(1900,db.ledgerDao().inScope("ALIPAY",new LocalSettings(ApplicationProvider.getApplicationContext()).accountScope("ALIPAY")).get(0).amountMinor);}
 @Test public void oldParseFailureUsesNewParserButOriginalCaptureDay(){long old=Instant.parse("2026-11-12T02:00:00Z").toEpochMilli();var e=intake.store(snapshot("old",old));e.processState="REVIEW";e.errorCode="INVALID_OR_CONFLICTING_FACTS";e.candidateJson=EventCodec.encodeParsed(new ParseResult(ParseResult.Disposition.REVIEW,null,e.errorCode,"old",1));db.sourceEventDao().update(e);assertEquals(1,replay.process(Collections.singletonList(snapshot("old",clock.millis()))).posted);var tx=db.ledgerDao().find(db.sourceEventDao().find(e.id).transactionId);assertEquals(TimeWindows.dayAnchor(LocalDate.of(2026,11,12)),tx.occurredAtMs);assertEquals(old,db.sourceEventDao().find(e.id).capturedAtMs);}
 @Test public void ignoreDeleteAndWeakDuplicateArePreserved(){var s=snapshot("first",clock.millis());replay.process(Collections.singletonList(s));var e=intake.store(s);var tx=db.ledgerDao().find(e.transactionId);new ManualLedgerRepository(db,clock).delete(tx.id,tx.revision);assertEquals(1,replay.process(Collections.singletonList(s)).unchanged);assertEquals(0,db.ledgerDao().countPosted());var other=snapshot("other",clock.millis());assertEquals(1,replay.process(Collections.singletonList(other)).review);var ignored=intake.store(other);new ReviewRepository(db,clock).ignore(ignored.id);assertEquals(1,replay.process(Collections.singletonList(other)).unchanged);assertEquals("USER_IGNORE",db.sourceEventDao().find(ignored.id).reviewResolution);assertEquals(0,db.ledgerDao().countPosted());}
 @Test public void expiredFailureIsNotRevivedByCurrentNotification(){long old=clock.millis()-8*86400000L;var s=snapshot("expired",old);var e=intake.store(s);e.processState="REVIEW";e.errorCode="INVALID_OR_CONFLICTING_FACTS";e.rawPayload=null;db.sourceEventDao().update(e);assertEquals(1,replay.process(Collections.singletonList(snapshot("expired",clock.millis()))).review);assertNull(db.sourceEventDao().find(e.id).rawPayload);assertEquals(0,db.ledgerDao().countPosted());}
 @Test public void emptyAndUnrelatedNoticesHaveUsefulCounts(){assertEquals(0,replay.process(Collections.emptyList()).seen);var s=new NotificationSnapshot("com.tencent.mm","chat",1,clock.millis(),"chat","朋友","你好","",Collections.emptyList(),false,false,"8.0.76");assertEquals(1,replay.process(Collections.singletonList(s)).ignored);assertTrue(db.sourceEventDao().pending(10).isEmpty());}
}
