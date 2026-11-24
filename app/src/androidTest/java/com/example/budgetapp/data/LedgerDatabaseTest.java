package com.example.budgetapp.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.data.entity.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LedgerDatabaseTest {
    private LedgerDatabase db;
    private SupportSQLiteDatabase sql;
    @Before public void setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),LedgerDatabase.class)
                .addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();
        sql = db.getOpenHelper().getWritableDatabase();
        sql.execSQL("INSERT INTO ledger_transaction (id,kind,amount_minor,currency,occurred_at_ms,time_precision,source_zone_id,merchant_raw,merchant_normalized,provider,account_scope_id,category_id,category_origin,booking_state,user_locked_fields,note,revision,created_at,updated_at) VALUES ('tx-1','EXPENSE',2880,'CNY',100,'SECOND','Asia/Shanghai','','','WECHAT','scope','00000000-0000-0000-0000-000000000001','DEFAULT','POSTED','[]','',1,100,100)");
    }
    @After public void close() { db.close(); }
    @Test public void postedFilteringAndDefaultsAreStable() {
        assertEquals(1,db.ledgerDao().countPosted());
        assertEquals(2880L,db.ledgerDao().find("tx-1").amountMinor);
        assertEquals(1,db.ledgerDao().listPosted(100,101,null,null,null).size());
        assertTrue(db.ledgerDao().listPosted(0,100,null,null,null).isEmpty());
        assertTrue(db.ledgerDao().listPosted(0,200,"INCOME",null,null).isEmpty());
        assertTrue(db.ledgerDao().listPosted(0,200,null,null,"ALIPAY").isEmpty());
        sql.execSQL("UPDATE ledger_transaction SET booking_state='DELETED' WHERE id='tx-1'");
        assertEquals(0,db.ledgerDao().countPosted());
        LedgerDatabase.seedDefaults(sql); LedgerDatabase.seedDefaults(sql);
        assertEquals(5,count("SELECT COUNT(*) FROM category"));
    }
    @Test public void identityUsesFullUniqueKeyAndForeignKey() {
        identity("i1","scope","platform");
        assertThrows(SQLiteConstraintException.class,()->identity("i2","scope","platform"));
        identity("i3","scope2","platform"); identity("i4","scope","merchant");
        assertEquals(3,count("SELECT COUNT(*) FROM transaction_identity"));
        assertThrows(SQLiteConstraintException.class,()->sql.execSQL("DELETE FROM ledger_transaction WHERE id='tx-1'"));
    }
    @Test public void eventsAreUniqueNullableAndManyToOne() {
        event("e1","slot1","tx-1","DONE"); event("e2","slot2","tx-1","DONE");
        event("e3","slot3",null,"REVIEW"); event("e4","slot4",null,"NEW");
        assertEquals(1,db.sourceEventDao().reviews().size());
        assertEquals(1,db.sourceEventDao().pending(20).size());
        assertThrows(SQLiteConstraintException.class,()->event("bad","bad","missing","NEW"));
        assertThrows(SQLiteConstraintException.class,()->event("duplicate","slot1",null,"NEW"));
        assertEquals(4,count("SELECT COUNT(*) FROM source_event"));
    }
    @Test public void businessTablesAreExactlyFive() {
        Set<String> actual = new HashSet<>();
        try (Cursor c=sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table')")) {
            while(c.moveToNext()) actual.add(c.getString(0));
        }
        assertEquals(new HashSet<>(Arrays.asList("source_event","ledger_transaction","category","category_rule","transaction_identity")),actual);
    }
    @Test public void constraintsProtectAmountsEnumsCategoriesAndMergeCycles() {
        for(String assignment:new String[]{"amount_minor=0","amount_minor=-1","currency='USD'","kind='REFUND'","booking_state='BAD'","merged_into_id='tx-1'","category_id='missing'"}) {
            assertThrows(assignment,SQLiteConstraintException.class,()->sql.execSQL("UPDATE ledger_transaction SET "+assignment+" WHERE id='tx-1'"));
        }
        assertThrows(SQLiteConstraintException.class,()->sql.execSQL("DELETE FROM category WHERE id='00000000-0000-0000-0000-000000000001'"));
        sql.execSQL("INSERT INTO category_rule (id,match_type,merchant_pattern,category_id,priority,enabled,version) VALUES ('rule','EXACT','shop','00000000-0000-0000-0000-000000000003',1,1,1)");
        assertThrows(SQLiteConstraintException.class,()->sql.execSQL("DELETE FROM category WHERE id='00000000-0000-0000-0000-000000000003'"));
        sql.execSQL("INSERT INTO ledger_transaction SELECT 'tx-2',kind,amount_minor,currency,occurred_at_ms,time_precision,source_zone_id,merchant_raw,merchant_normalized,provider,account_scope_id,pay_method_raw,category_id,category_origin,applied_rule_id,booking_state,deleted_at,user_locked_fields,note,revision,created_at,updated_at,deletion_reason,merged_into_id,recent_previous_values_json FROM ledger_transaction WHERE id='tx-1'");
        sql.execSQL("UPDATE ledger_transaction SET merged_into_id='tx-2' WHERE id='tx-1'");
        assertThrows(SQLiteConstraintException.class,()->sql.execSQL("UPDATE ledger_transaction SET merged_into_id='tx-1' WHERE id='tx-2'"));
    }
    @Test public void settingsPreserveScopeAndValidateRetention() {
        Context context=ApplicationProvider.getApplicationContext();
        LocalSettings s=new LocalSettings(context);
        String old=s.accountScope("WECHAT");
        assertEquals(old,new LocalSettings(context).accountScope("WECHAT"));
        assertNotEquals(old,s.replaceAccountScope("WECHAT","本人新账号"));
        assertNotNull(context.getSharedPreferences("local_settings",Context.MODE_PRIVATE).getString("alias."+old,null));
        assertThrows(IllegalArgumentException.class,()->s.setRetentionDays(8));
        s.setRetentionDays(30); assertEquals(30,s.retentionDays()); s.setRetentionDays(7);
    }
    private int count(String query) { try(Cursor c=sql.query(query)) { c.moveToFirst(); return c.getInt(0); } }
    private void identity(String id,String scope,String namespace) {
        sql.execSQL("INSERT INTO transaction_identity VALUES (?,?,?,?,?,?,?)",new Object[]{id,"tx-1","WECHAT",scope,"ORDER",namespace,"order-1"});
    }
    private void event(String id,String key,String tx,String state) {
        sql.execSQL("INSERT INTO source_event (id,source_type,provider,account_scope_id,captured_at_ms,event_key,process_state,transaction_id) VALUES (?,'NOTIFICATION','WECHAT','scope',100,?,?,?)",new Object[]{id,key,state,tx});
    }
}
