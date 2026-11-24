package com.example.budgetapp.data;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.*;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.nio.file.*;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class DatabaseUpgradeSafetyTest {
 @Rule public MigrationTestHelper helper=new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),LedgerDatabase.class.getCanonicalName(),new FrameworkSQLiteOpenHelperFactory());
 private final String original="upgrade-v1-fixture",future="upgrade-future-copy";
 private Context base(){return ApplicationProvider.getApplicationContext();}
 private Context named(String file){return new ContextWrapper(base()){@Override public Context getApplicationContext(){return this;}@Override public File getDatabasePath(String ignored){return base().getDatabasePath(file);}};}
 private void createV1() throws Exception {var db=helper.createDatabase(original,1);LedgerDatabase.seedDefaults(db);for(String id:new String[]{"edited","deleted"})db.execSQL("INSERT INTO ledger_transaction (id,kind,amount_minor,currency,occurred_at_ms,time_precision,source_zone_id,merchant_raw,merchant_normalized,provider,account_scope_id,category_id,category_origin,booking_state,user_locked_fields,note,revision,created_at,updated_at) VALUES (?,'EXPENSE',8000,'CNY',100,'DAY','Asia/Shanghai','店','店','WECHAT','scope','00000000-0000-0000-0000-000000000001','USER',?,'[\"amount\",\"category\"]','',3,100,200)",new Object[]{id,id.equals("deleted")?"DELETED":"POSTED"});for(int i=1;i<=2;i++)db.execSQL("INSERT INTO source_event (id,source_type,provider,account_scope_id,captured_at_ms,raw_payload,event_key,parsed_json,process_state,transaction_id) VALUES (?,'NOTIFICATION','WECHAT','scope',0,'private raw',?,'accepted-10000','DONE','edited')",new Object[]{"event"+i,"key"+i});db.execSQL("INSERT INTO source_event (id,source_type,provider,account_scope_id,captured_at_ms,event_key,process_state,review_reason_codes_json) VALUES ('review','NOTIFICATION','WECHAT','scope',0,'review','REVIEW','[\"MISSING_DATE\"]')");db.execSQL("INSERT INTO transaction_identity VALUES ('identity','edited','WECHAT','scope','ORDER','platform','12345678901234567890')");db.close();}
 @Test public void realOneToTwoMigrationPreservesLedgerEvidenceAndReview() throws Exception {createV1();var upgraded=helper.runMigrationsAndValidate(original,2,true,LedgerDatabase.MIGRATION_1_2);upgraded.close();var db=LedgerDatabase.open(named(original));try{assertEquals(8000,db.ledgerDao().find("edited").amountMinor);assertEquals("DELETED",db.ledgerDao().find("deleted").bookingState);assertEquals("[\"amount\",\"category\"]",db.ledgerDao().find("edited").userLockedFields);assertEquals(2,db.sourceEventDao().evidence("edited").size());assertEquals(1,db.sourceEventDao().listReview().size());assertEquals("edited",db.ledgerDao().identity("WECHAT","scope","ORDER","platform","12345678901234567890").transactionId);var settings=new LocalSettings(base());settings.setRetentionDays(7);new RawRetention(db,settings,Clock.fixed(Instant.ofEpochMilli(8*86400000L),ZoneOffset.UTC)).purgeExpired();assertNull(db.sourceEventDao().find("event1").rawPayload);assertEquals("accepted-10000",db.sourceEventDao().find("event1").parsedJson);}finally{db.close();}}
 @Test public void unknownFutureVersionFailsWithoutDeletingCopiedFacts() throws Exception {createV1();helper.runMigrationsAndValidate(original,2,true,LedgerDatabase.MIGRATION_1_2).close();Files.copy(base().getDatabasePath(original).toPath(),base().getDatabasePath(future).toPath(),StandardCopyOption.REPLACE_EXISTING);try(SQLiteDatabase sql=SQLiteDatabase.openDatabase(base().getDatabasePath(future).getPath(),null,SQLiteDatabase.OPEN_READWRITE)){sql.setVersion(999);}var database=LedgerDatabase.open(named(future));try{assertThrows(RuntimeException.class,()->database.getOpenHelper().getWritableDatabase());}finally{database.close();}try(SQLiteDatabase sql=SQLiteDatabase.openDatabase(base().getDatabasePath(future).getPath(),null,SQLiteDatabase.OPEN_READONLY);android.database.Cursor c=sql.rawQuery("SELECT amount_minor FROM ledger_transaction WHERE id='edited'",null)){assertTrue(c.moveToFirst());assertEquals(8000,c.getLong(0));assertEquals(999,sql.getVersion());}}
}
