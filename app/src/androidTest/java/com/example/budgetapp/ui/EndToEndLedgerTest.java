package com.example.budgetapp.ui;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.data.*;
import com.example.budgetapp.data.entity.*;
import com.example.budgetapp.parsing.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.stats.*;
import java.time.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class EndToEndLedgerTest {
 @Test public void editsDeletesRetentionAndStatsStayConsistent(){var context=ApplicationProvider.getApplicationContext();var db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase.class).addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();try{Clock clock=Clock.fixed(Instant.parse("2026-11-11T00:00:00Z"),ZoneOffset.UTC);var parser=new NotificationParser(TemplateCatalog.fromJson("{\"formatVersion\":1,\"templates\":[]}"));var processor=new TransactionProcessor(db,parser,new CategoryMatcher(),clock);var manual=new ManualLedgerRepository(db,clock);String event=event(db,"original",10000);var posted=processor.process(event);assertEquals(TransactionProcessor.ProcessResult.Status.POSTED,posted.status);manual.update(posted.transactionId,1,new TransactionDraft(Kind.EXPENSE,8000,1793376000000L,TimePrecision.DAY,"店","WECHAT","scope",null,""));var settings=new LocalSettings(context);settings.setRetentionDays(7);new RawRetention(db,settings,clock).purgeExpired();assertEquals(TransactionProcessor.ProcessResult.Status.LINKED,processor.process(event(db,"replay",10000)).status);assertEquals(8000,db.ledgerDao().find(posted.transactionId).amountMinor);var deleted=manual.delete(posted.transactionId,2);processor.process(event(db,"deleted-replay",10000));assertEquals(0,db.ledgerDao().countPosted());manual.undoDelete(posted.transactionId,deleted.revision);manual.save("income",new TransactionDraft(Kind.INCOME,300000,1793376000000L,TimePrecision.DAY,"工资","WECHAT","scope",null,""),true);var stats=new StatsRepository(db,Runnable::run);var snapshot=stats.load(new LedgerFilter(0,Long.MAX_VALUE,LedgerFilter.Metric.NET_OUTFLOW,null,null));assertEquals(8000,snapshot.expenseFen);assertEquals(-292000,snapshot.netOutflowFen);assertEquals(snapshot.valueFen,snapshot.entries.stream().mapToLong(e->e.contributionFen).sum());String refund=event(db,"refund",10000);var row=db.sourceEventDao().find(refund);row.candidateJson=EventCodec.encodeParsed(new ParseResult(ParseResult.Disposition.IGNORED,null,"REFUND_UNSUPPORTED","synthetic",1));db.sourceEventDao().update(row);assertEquals(TransactionProcessor.ProcessResult.Status.IGNORED,processor.process(refund).status);assertEquals(2,db.ledgerDao().countPosted());}finally{db.close();}}
 private String event(LedgerDatabase db,String key,long amount){var p=new ParsedTransaction();p.kind=Kind.EXPENSE;p.amountMinor=amount;p.currency="CNY";p.occurredAtMs=1793376000000L;p.timePrecision=TimePrecision.DAY;p.merchant="店";p.tradeStatus=ParsedTransaction.TradeStatus.SUCCESS;p.proofs.addAll(EnumSet.allOf(ParsedTransaction.Proof.class));p.identities.add(new ParsedTransaction.Identity("ORDER","platform","same-order"));var e=new SourceEventEntity();e.id=key;e.sourceType="NOTIFICATION";e.provider="WECHAT";e.accountScopeId="scope";e.capturedAtMs=0;e.rawPayload="expired raw";e.eventKey=key;e.processState="NEW";e.candidateJson=EventCodec.encodeParsed(new ParseResult(ParseResult.Disposition.CANDIDATE,p,"OK","synthetic",1));db.sourceEventDao().insertIgnore(e);return key;}
}
