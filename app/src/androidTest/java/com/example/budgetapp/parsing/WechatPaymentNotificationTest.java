package com.example.budgetapp.parsing;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.testing.WorkManagerTestInitHelper;
import com.example.budgetapp.capture.notification.*;
import com.example.budgetapp.data.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.worker.RecoveryScheduler;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static com.example.budgetapp.parsing.ParseResult.Disposition.*;

@RunWith(AndroidJUnit4.class) public class WechatPaymentNotificationTest {
 private final long captured=Instant.parse("2026-11-12T06:31:04Z").toEpochMilli();
 private NotificationParser parser(){return new NotificationParser(TemplateCatalog.load(ApplicationProvider.getApplicationContext()));}
 private NotificationSnapshot sample(String text,String big,List<String> lines,String channel,String title,String version,boolean summary,boolean truncated,long at){return new NotificationSnapshot("com.tencent.mm","test-slot",Instant.parse("2026-11-11T15:59:59Z").toEpochMilli(),at,channel,title,text,big,lines,summary,truncated,version);}
 private NotificationSnapshot sample(String text){return sample(text,"",Collections.emptyList(),"message_channel_new_id","微信支付","8.0.76",false,false,captured);}
 @Test public void deviceBodyWithoutRepeatedTitleIsRecognized(){for(String body:new String[]{"已支付¥0.01","已支付￥0.01","[3条]微信支付: 已支付¥0.01","微信支付： 已支付￥0.01"}){var snapshot=sample(body);assertEquals(NotificationParser.IntakeDecision.TRADE,parser().classify(snapshot));var result=parser().parse(snapshot);assertEquals(CANDIDATE,result.disposition);assertEquals(Long.valueOf(1),result.candidate.amountMinor);assertEquals("",result.candidate.merchant);assertEquals(Long.valueOf(Instant.parse("2026-11-11T15:59:59Z").toEpochMilli()),result.candidate.occurredAtMs);}assertEquals(CANDIDATE,parser().parse(sample("已支付¥0.01","",Collections.emptyList(),"message_channel_new_id","朋友","8.0.76",false,false,captured)).disposition);assertEquals(NotificationParser.IntakeDecision.DROP,parser().classify(sample("退款成功¥0.01")));}
 @Test public void realFormatUsesNotificationPostTimeWithoutMerchant() throws Exception {
  var parser=parser();assertEquals(NotificationParser.IntakeDecision.TRADE,parser.classify(sample("[3条]微信支付：已支付￥0.01")));
  for(String text:new String[]{"[3条]微信支付：已支付￥0.01","[12条]微信支付：已支付￥0.01","微信支付：已支付￥0.01"}){
   var r=parser.parse(sample(text));assertEquals(CANDIDATE,r.disposition);assertEquals(Long.valueOf(1),r.candidate.amountMinor);assertEquals(Kind.EXPENSE,r.candidate.kind);assertEquals("CNY",r.candidate.currency);assertEquals("",r.candidate.merchant);assertEquals(TimePrecision.SECOND,r.candidate.timePrecision);assertEquals(Long.valueOf(Instant.parse("2026-11-11T15:59:59Z").toEpochMilli()),r.candidate.occurredAtMs);assertTrue(r.candidate.identities.isEmpty());
   var encoded=EventCodec.encodeParsed(r);assertEquals("NOTIFICATION_POST_TIME",new JSONObject(encoded).getJSONObject("candidate").getString("dateBasis"));assertEquals(encoded,EventCodec.encodeParsed(EventCodec.decodeParsed(encoded)));
  }
 }
 @Test public void localMidnightKeepsPostTimeRatherThanCaptureTime(){var r=parser().parse(sample("微信支付：已支付￥2.00","",Collections.emptyList(),"message_channel_new_id","微信支付","8.0.76",false,false,Instant.parse("2026-11-12T16:00:00Z").toEpochMilli()));assertEquals(CANDIDATE,r.disposition);assertEquals(Long.valueOf(Instant.parse("2026-11-11T15:59:59Z").toEpochMilli()),r.candidate.occurredAtMs);}
 @Test public void unrelatedNotificationsAndRefundsAreIgnored(){for(String text:new String[]{"你好，已支付￥0.01","[3条]微信支付：退款成功￥0.01","微信支付：待支付￥0.01","微信支付：支付失败￥0.01"})assertEquals(NotificationParser.IntakeDecision.DROP,parser().classify(sample(text)));for(NotificationSnapshot s:new NotificationSnapshot[]{sample("微信支付：已支付￥0.01","",Collections.emptyList(),"other","微信支付","8.0.76",false,false,captured),sample("微信支付：已支付￥0.01","",Collections.emptyList(),"message_channel_new_id","朋友","8.0.76",false,false,captured)})assertEquals(CANDIDATE,parser().parse(s).disposition);}
 @Test public void uncertainFlagsVersionsAndMalformedAmountsNeedReview(){for(String version:new String[]{"8.0.77",""})assertEquals(CANDIDATE,parser().parse(sample("微信支付：已支付￥0.01","",Collections.emptyList(),"message_channel_new_id","微信支付",version,false,false,captured)).disposition);for(boolean summary:new boolean[]{true,false})assertEquals(REVIEW,parser().parse(sample("微信支付：已支付￥0.01","",Collections.emptyList(),"message_channel_new_id","微信支付","8.0.76",summary,!summary,captured)).disposition);for(String amount:new String[]{"0.00","-1.00","0.001","999999999999999999999999","1.00 请转账"})assertEquals(REVIEW,parser().parse(sample("微信支付：已支付￥"+amount)).disposition);}
 @Test public void conflictingExtendedTextCannotSilentlySelectOneAmount(){assertEquals(REVIEW,parser().parse(sample("微信支付：已支付￥0.01","微信支付：已支付￥9.00",Collections.emptyList(),"message_channel_new_id","微信支付","8.0.76",false,false,captured)).disposition);assertEquals(CANDIDATE,parser().parse(sample("微信支付：已支付￥0.01","微信支付：已支付￥0.01",Collections.emptyList(),"message_channel_new_id","微信支付","8.0.76",false,false,captured)).disposition);}
 @Test public void intakeBooksOneUncategorizedExpenseAndReplayIsIdempotent() throws Exception {
  var context=ApplicationProvider.getApplicationContext();var db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase.class).addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();ExecutorService io=Executors.newSingleThreadExecutor();Clock clock=Clock.fixed(Instant.ofEpochMilli(captured),ZoneOffset.UTC);
  try {WorkManagerTestInitHelper.initializeTestWorkManager(context,new Configuration.Builder().setExecutor(command->{}).build());var parser=parser();var intake=new EventIntake(db,parser,new LocalSettings(context),new RecoveryScheduler(context),io);var snapshot=sample("已支付¥0.01");intake.accept(snapshot);intake.accept(snapshot);io.submit(()->{}).get();assertEquals(1,db.sourceEventDao().pending(10).size());var processor=new TransactionProcessor(db,parser,new CategoryMatcher(),clock);var event=db.sourceEventDao().pending(10).get(0);var result=processor.process(event.id);assertEquals(TransactionProcessor.ProcessResult.Status.POSTED,result.status);var tx=db.ledgerDao().find(result.transactionId);assertEquals(1,tx.amountMinor);assertEquals("",tx.merchantRaw);assertEquals(LedgerTypes.UNCATEGORIZED_EXPENSE,tx.categoryId);assertEquals("待归类支出",db.categoryDao().find(tx.categoryId).name);assertEquals("",tx.note);assertEquals(TransactionProcessor.ProcessResult.Status.ALREADY_HANDLED,processor.process(event.id).status);intake.accept(snapshot);io.submit(()->{}).get();assertTrue(db.sourceEventDao().pending(10).isEmpty());assertEquals(1,db.ledgerDao().countPosted());
   var stats=new com.example.budgetapp.stats.StatsRepository(db,Runnable::run).load(new LedgerFilter(TimeWindows.dayAnchor(LocalDate.of(2026,11,11)),TimeWindows.dayAnchor(LocalDate.of(2026,11,12)),LedgerFilter.Metric.EXPENSE,LedgerTypes.UNCATEGORIZED_EXPENSE,"WECHAT"));assertEquals(1,stats.expenseFen);assertEquals(1,stats.entries.size());assertEquals("待归类支出",stats.categories.get(0).name);
   // Another indistinguishable same-day payment is reviewed, never merged or booked twice blindly.
   var later=sample("[4条]微信支付：已支付￥0.01");intake.accept(later);io.submit(()->{}).get();assertEquals(TransactionProcessor.ProcessResult.Status.REVIEW,processor.process(db.sourceEventDao().pending(10).get(0).id).status);assertEquals(1,db.ledgerDao().countPosted());
  } finally {io.shutdownNow();db.close();}
 }
 @Test public void existingDefaultCategoryIsRenamedWithoutOverwritingCustomName(){var context=ApplicationProvider.getApplicationContext();String file="category-label-"+UUID.randomUUID()+".db";LedgerDatabase db=null;try {db=Room.databaseBuilder(context,LedgerDatabase.class,file).addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();var category=db.categoryDao().find(LedgerTypes.UNCATEGORIZED_EXPENSE);category.name="未分类支出";db.categoryDao().update(category);db.close();db=Room.databaseBuilder(context,LedgerDatabase.class,file).addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();assertEquals("待归类支出",db.categoryDao().find(category.id).name);category.name="自己命名";db.categoryDao().update(category);db.close();db=Room.databaseBuilder(context,LedgerDatabase.class,file).addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();assertEquals("自己命名",db.categoryDao().find(category.id).name);}finally {if(db!=null)db.close();context.deleteDatabase(file);}}
}
