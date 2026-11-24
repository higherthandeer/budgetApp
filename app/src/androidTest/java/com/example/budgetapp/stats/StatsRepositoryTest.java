package com.example.budgetapp.stats;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.data.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.ui.UiTestSupport;
import java.time.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class StatsRepositoryTest {
 private LedgerDatabase db;private StatsRepository repo;private ManualLedgerRepository manual;private ExecutorService io;
 private LedgerFilter november(){var r=TimeWindows.month(YearMonth.of(2026,11));return new LedgerFilter(r.startMs,r.endMs,LedgerFilter.Metric.NET_OUTFLOW,null,null);}
 @Before public void setUp(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),LedgerDatabase.class).addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();io=Executors.newSingleThreadExecutor();repo=new StatsRepository(db,io);manual=new ManualLedgerRepository(db,Clock.systemUTC());}
 @After public void close(){io.shutdownNow();db.close();}
 private String add(String key,Kind kind,long amount,String category,String provider){return manual.save(key,new TransactionDraft(kind,amount,TimeWindows.dayAnchor(LocalDate.of(2026,11,5)),TimePrecision.DAY,"商家",provider,"scope",category,""),true).transactionId;}
 @Test public void totalsCategoriesAndSignedEntriesShareOneScope(){add("food",Kind.EXPENSE,2880,null,"WECHAT");add("travel",Kind.EXPENSE,1000,"00000000-0000-0000-0000-000000000004","ALIPAY");add("income",Kind.INCOME,300000,null,"OTHER");String deleted=add("deleted",Kind.EXPENSE,9999,null,"WECHAT");manual.delete(deleted,1);var s=repo.load(november());assertEquals(3880,s.expenseFen);assertEquals(300000,s.incomeFen);assertEquals(-296120,s.netOutflowFen);assertEquals(s.valueFen,s.entries.stream().mapToLong(e->e.contributionFen).sum());assertEquals(s.expenseFen,s.categories.stream().mapToLong(c->c.amountFen).sum());assertEquals(2880,repo.load(november().withProvider("WECHAT")).expenseFen);assertEquals(1000,repo.load(november().withCategory("00000000-0000-0000-0000-000000000004")).valueFen);assertEquals(0,repo.load(november().withRange(0,0)).valueFen);}
 @Test public void deletedUndoAndArchivedCategoryRetainCorrectHistory(){String id=add("food",Kind.EXPENSE,2880,"00000000-0000-0000-0000-000000000003","WECHAT");new CategoryRepository(db,Clock.systemUTC()).archiveCategory("00000000-0000-0000-0000-000000000003");assertEquals(2880,repo.load(november()).expenseFen);var deleted=manual.delete(id,1);assertEquals(0,repo.load(november()).expenseFen);manual.undoDelete(id,deleted.revision);assertEquals(2880,repo.load(november()).expenseFen);manual.update(id,3,new TransactionDraft(Kind.EXPENSE,2880,TimeWindows.dayAnchor(LocalDate.of(2026,12,1)),TimePrecision.DAY,"商家","WECHAT","scope",null,""));assertEquals(0,repo.load(november()).expenseFen);}
 @Test public void overflowIsAnExplicitError(){add("max",Kind.EXPENSE,Long.MAX_VALUE,null,"WECHAT");add("one",Kind.EXPENSE,1,null,"WECHAT");assertThrows(StatsRepository.StatisticsException.class,()->repo.load(november()));}
 @Test public void observedTotalsReactToWrites() throws Exception {var live=repo.observe(november());UiTestSupport.awaitValue(live,s->s.valid&&s.expenseFen==0);add("observed",Kind.EXPENSE,2880,null,"WECHAT");assertEquals(2880,UiTestSupport.awaitValue(live,s->s.valid&&s.expenseFen==2880).expenseFen);}
 @Test public void observedCurrentPeriodAdvancesItsEndOnInvalidation() throws Exception {
  java.util.concurrent.atomic.AtomicLong end=new java.util.concurrent.atomic.AtomicLong(november().startMs+1);
  var live=repo.observe(()->november().withRange(november().startMs,end.get()));
  androidx.lifecycle.Observer<StatsRepository.Snapshot> keeper=value->{};
  var instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
  instrumentation.runOnMainSync(()->live.observeForever(keeper));
  try {UiTestSupport.awaitValue(live,s->s.valid&&s.expenseFen==0);end.set(november().endMs);add("later",Kind.EXPENSE,2880,null,"WECHAT");assertEquals(2880,UiTestSupport.awaitValue(live,s->s.valid&&s.expenseFen==2880).expenseFen);}
  finally {instrumentation.runOnMainSync(()->live.removeObserver(keeper));}
 }
}
