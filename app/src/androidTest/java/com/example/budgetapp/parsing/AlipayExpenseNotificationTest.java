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
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static com.example.budgetapp.parsing.ParseResult.Disposition.*;

@RunWith(AndroidJUnit4.class) public class AlipayExpenseNotificationTest {
 private final long captured=Instant.parse("2026-11-12T14:41:00Z").toEpochMilli();
 private NotificationParser parser(){return new NotificationParser(TemplateCatalog.load(ApplicationProvider.getApplicationContext()));}
 private NotificationSnapshot sample(String text,String big,String title,String channel,String version,boolean summary,boolean truncated){return new NotificationSnapshot("com.eg.android.AlipayGphone","test-alipay-slot",captured-86400000,captured,channel,title,text,big,Collections.emptyList(),summary,truncated,version);}
 private NotificationSnapshot sample(String text){return sample(text,"","交易提醒","alipay_default","12.12.12.8000",false,false);}
 @Test public void userChosenPrefixRuleIgnoresDetailAndPromotionTails(){for(String text:new String[]{"你有一笔19.00元的支出，点此查看详情","你有一笔19.00元的支出，另付9元。"}){var result=parser().parse(sample(text));assertEquals(CANDIDATE,result.disposition);assertEquals(Long.valueOf(1900),result.candidate.amountMinor);}}
 @Test public void screenshotFormatsUseExpenseAmountNotCouponOrPoints(){for(String text:new String[]{"你有一笔0.01元的支出，点击领取2个支付宝积分。","你有一笔0.01元的支出，点击领1元话费券。","你有一笔0.01元的支出。"}){var snapshot=sample(text);assertEquals(NotificationParser.IntakeDecision.TRADE,parser().classify(snapshot));var result=parser().parse(snapshot);assertEquals(CANDIDATE,result.disposition);var p=result.candidate;assertEquals(Long.valueOf(1),p.amountMinor);assertEquals(Kind.EXPENSE,p.kind);assertEquals("CNY",p.currency);assertEquals("",p.merchant);assertEquals(Long.valueOf(captured-86400000),p.occurredAtMs);assertEquals(TimePrecision.SECOND,p.timePrecision);assertEquals(ParsedTransaction.DateBasis.NOTIFICATION_POST_TIME,p.dateBasis);assertTrue(p.identities.isEmpty());}}
 @Test public void sourceBoundariesAndUncertainFlagsAreRespected(){String body="你有一笔0.01元的支出，点击领取2个支付宝积分。";assertEquals(CANDIDATE,parser().parse(sample(body,"","优惠提醒","alipay_default","12.12.12.8000",false,false)).disposition);assertEquals(CANDIDATE,parser().parse(sample(body,"","交易提醒","other","12.12.12.8000",false,false)).disposition);for(NotificationSnapshot s:new NotificationSnapshot[]{sample(body,"","交易提醒","alipay_default","12.12.12.8000",true,false),sample(body,"","交易提醒","alipay_default","12.12.12.8000",false,true)})assertEquals(REVIEW,parser().parse(s).disposition);assertEquals(NotificationParser.IntakeDecision.DROP,parser().classify(sample("点击领1元话费券。")));}
 @Test public void refundIncomeAndFailedPaymentDoNotBecomeExpense(){for(String text:new String[]{"你有一笔0.01元的退款。","你有一笔0.01元的收入。","你有一笔0.01元的支出，支付失败。","你有一笔0.01元的支出，处理中。"})assertEquals(IGNORED,parser().parse(sample(text)).disposition);}
 @Test public void malformedAmountsRequireReview(){for(String text:new String[]{"你有一笔0.001元的支出。","你有一笔0元的支出。","你有一笔-1元的支出。","你有一笔999999999999999999999元的支出。"})assertEquals(REVIEW,parser().parse(sample(text)).disposition);}
 @Test public void conflictingExpandedAmountsAreNotSilentlyChosen(){assertEquals(REVIEW,parser().parse(sample("你有一笔0.01元的支出。","你有一笔9元的支出。","交易提醒","alipay_default","12.12.12.8000",false,false)).disposition);assertEquals(CANDIDATE,parser().parse(sample("你有一笔0.01元的支出。","你有一笔0.01元的支出，点击领取2个支付宝积分。","交易提醒","alipay_default","12.12.12.8000",false,false)).disposition);}
 @Test public void intakeBooksUncategorizedExpenseOnceAndFlagsSameAmount() throws Exception {
  var context=ApplicationProvider.getApplicationContext();var db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase.class).addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();ExecutorService io=Executors.newSingleThreadExecutor();Clock clock=Clock.fixed(Instant.ofEpochMilli(captured),ZoneOffset.UTC);
  try {WorkManagerTestInitHelper.initializeTestWorkManager(context,new Configuration.Builder().setExecutor(command->{}).build());var parser=parser();var intake=new EventIntake(db,parser,new LocalSettings(context),new RecoveryScheduler(context),io);var snapshot=sample("你有一笔0.01元的支出，点击领1元话费券。");intake.accept(snapshot);intake.accept(snapshot);io.submit(()->{}).get();assertEquals(1,db.sourceEventDao().pending(10).size());var event=db.sourceEventDao().pending(10).get(0);var processor=new TransactionProcessor(db,parser,new CategoryMatcher(),clock);var posted=processor.process(event.id);assertEquals(TransactionProcessor.ProcessResult.Status.POSTED,posted.status);var tx=db.ledgerDao().find(posted.transactionId);assertEquals("ALIPAY",tx.provider);assertEquals("",tx.merchantRaw);assertEquals(LedgerTypes.UNCATEGORIZED_EXPENSE,tx.categoryId);assertEquals(1,tx.amountMinor);assertEquals("",tx.note);assertEquals(TransactionProcessor.ProcessResult.Status.ALREADY_HANDLED,processor.process(event.id).status);
   var filter=new LedgerFilter(TimeWindows.dayAnchor(LocalDate.of(2026,11,11)),TimeWindows.dayAnchor(LocalDate.of(2026,11,12)),LedgerFilter.Metric.EXPENSE,LedgerTypes.UNCATEGORIZED_EXPENSE,"ALIPAY");var stats=new com.example.budgetapp.stats.StatsRepository(db,Runnable::run).load(filter);assertEquals(1,stats.expenseFen);assertEquals(1,stats.entries.size());
   intake.accept(sample("你有一笔0.01元的支出，点击领取2个支付宝积分。"));io.submit(()->{}).get();assertEquals(TransactionProcessor.ProcessResult.Status.REVIEW,processor.process(db.sourceEventDao().pending(10).get(0).id).status);assertEquals(1,db.ledgerDao().countPosted());
  } finally {io.shutdownNow();db.close();}
 }
}
