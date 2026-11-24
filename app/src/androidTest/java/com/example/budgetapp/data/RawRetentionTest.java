package com.example.budgetapp.data;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.data.entity.*;
import com.example.budgetapp.parsing.*;
import com.example.budgetapp.processing.*;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class RawRetentionTest {
 private LedgerDatabase db;private LocalSettings settings;private final long day=86400000L;
 @Before public void setUp(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),LedgerDatabase.class).addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();settings=new LocalSettings(ApplicationProvider.getApplicationContext());settings.setRetentionDays(7);}
 @After public void close(){settings.setRetentionDays(7);db.close();}
 private void event(String id,long captured){var e=new SourceEventEntity();e.id=id;e.sourceType="NOTIFICATION";e.provider="WECHAT";e.accountScopeId="scope";e.capturedAtMs=captured;e.eventKey=id;e.rawPayload="private raw";e.parsedJson="accepted facts";e.candidateJson="structured candidate";e.processState="REVIEW";e.reviewReasonCodesJson="[\"MISSING_DATE\"]";db.sourceEventDao().insertIgnore(e);}
 @Test public void sevenDayBoundaryHidesThenPurgesOnlyRaw(){event("expired",0);var before=new RawRetention(db,settings,Clock.fixed(Instant.ofEpochMilli(7*day-1),ZoneOffset.UTC));assertEquals("private raw",before.readRaw("expired"));var exact=new RawRetention(db,settings,Clock.fixed(Instant.ofEpochMilli(7*day),ZoneOffset.UTC));assertNull(exact.readRaw("expired"));assertNotNull(db.sourceEventDao().find("expired").rawPayload);assertEquals(1,exact.purgeExpired());var e=db.sourceEventDao().find("expired");assertNull(e.rawPayload);assertEquals("accepted facts",e.parsedJson);assertEquals("structured candidate",e.candidateJson);assertEquals("REVIEW",e.processState);}
 @Test public void thirtyDayExtensionCannotResurrectExpiredOrClearedRaw(){event("extend",0);var retention=new RawRetention(db,settings,Clock.fixed(Instant.ofEpochMilli(6*day),ZoneOffset.UTC));retention.extendEvent("extend",30);assertEquals(Long.valueOf(30*day),db.sourceEventDao().find("extend").rawExpiresAtMs);assertNotNull(new RawRetention(db,settings,Clock.fixed(Instant.ofEpochMilli(8*day),ZoneOffset.UTC)).readRaw("extend"));retention.clearRaw("extend");assertNull(retention.readRaw("extend"));event("too-late",0);assertThrows(IllegalArgumentException.class,()->new RawRetention(db,settings,Clock.fixed(Instant.ofEpochMilli(8*day),ZoneOffset.UTC)).extendEvent("too-late",30));}
 @Test public void expiredUnparsedNotificationIsNeverReparsed(){var e=new SourceEventEntity();e.id="old";e.sourceType="NOTIFICATION";e.provider="WECHAT";e.accountScopeId="scope";e.eventKey="old";e.processState="NEW";e.rawPayload="not a valid payload";e.capturedAtMs=0;db.sourceEventDao().insertIgnore(e);Clock clock=Clock.fixed(Instant.ofEpochMilli(8*day),ZoneOffset.UTC);var processor=new TransactionProcessor(db,new NotificationParser(TemplateCatalog.fromJson("{\"formatVersion\":1,\"templates\":[]}")),new CategoryMatcher(),clock);assertEquals("RAW_EXPIRED",processor.process("old").reasonCode);assertEquals(0,db.ledgerDao().countPosted());}
}
