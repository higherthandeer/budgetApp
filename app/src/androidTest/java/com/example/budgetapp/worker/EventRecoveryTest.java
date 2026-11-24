package com.example.budgetapp.worker;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.*;
import androidx.work.testing.WorkManagerTestInitHelper;
import com.example.budgetapp.data.*;
import com.example.budgetapp.data.entity.*;
import com.example.budgetapp.parsing.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.ui.UiTestSupport;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class EventRecoveryTest {
 @Test public void enqueueDuringDrainIsNotLost() throws Exception {
  Context context=ApplicationProvider.getApplicationContext();var db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase.class).addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();var processor=new TransactionProcessor(db,new NotificationParser(TemplateCatalog.fromJson("{\"formatVersion\":1,\"templates\":[]}")),new CategoryMatcher(),Clock.systemUTC());CountDownLatch drained=new CountDownLatch(1),finish=new CountDownLatch(1);AtomicBoolean first=new AtomicBoolean(true);ExecutorService executor=Executors.newFixedThreadPool(3);
  WorkerFactory factory=new WorkerFactory(){@Override public ListenableWorker createWorker(@NonNull Context appContext,@NonNull String name,@NonNull WorkerParameters params){return new Worker(appContext,params){@NonNull @Override public Result doWork(){Result result=new ProcessEventsWorker(appContext,params,db,processor).doWork();if(first.getAndSet(false)){drained.countDown();try{if(!finish.await(10,TimeUnit.SECONDS))return Result.failure();}catch(InterruptedException e){Thread.currentThread().interrupt();return Result.failure();}}return result;}};}};
  try{WorkManagerTestInitHelper.initializeTestWorkManager(context,new Configuration.Builder().setExecutor(executor).setWorkerFactory(factory).build());var scheduler=new RecoveryScheduler(context);insert(db,"one",100);scheduler.schedule();assertTrue(drained.await(10,TimeUnit.SECONDS));insert(db,"two",200);scheduler.schedule();finish.countDown();var work=WorkManager.getInstance(context);UiTestSupport.awaitValue(work.getWorkInfosForUniqueWorkLiveData("process-source-events"),infos->infos.size()>=2&&infos.stream().allMatch(info->info.getState()==WorkInfo.State.SUCCEEDED));assertEquals(2,db.ledgerDao().countPosted());
  }finally{finish.countDown();executor.shutdownNow();db.close();}
 }
 private void insert(LedgerDatabase db,String key,long amount){ParsedTransaction p=new ParsedTransaction();p.kind=Kind.EXPENSE;p.amountMinor=amount;p.currency="CNY";p.occurredAtMs=100L;p.timePrecision=TimePrecision.SECOND;p.tradeStatus=ParsedTransaction.TradeStatus.SUCCESS;p.proofs.addAll(EnumSet.allOf(ParsedTransaction.Proof.class));SourceEventEntity e=new SourceEventEntity();e.id=key;e.sourceType="NOTIFICATION";e.provider="WECHAT";e.accountScopeId="scope";e.eventKey=key;e.processState="NEW";e.candidateJson=EventCodec.encodeParsed(new ParseResult(ParseResult.Disposition.CANDIDATE,p,"OK","synthetic",1));db.sourceEventDao().insertIgnore(e);}
}
