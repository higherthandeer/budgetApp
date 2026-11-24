package com.example.budgetapp.stats;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.data.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import java.time.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class SpendingComparisonQueryTest {
 @Test public void bothPeriodsKeepProviderAndCategoryScope(){var db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),LedgerDatabase.class).addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();try{var manual=new ManualLedgerRepository(db,Clock.systemUTC());for(int month:new int[]{10,11})for(String provider:new String[]{"WECHAT","ALIPAY"})manual.save(month+provider,new TransactionDraft(Kind.EXPENSE,provider.equals("WECHAT")?(month==11?200:100):9999,TimeWindows.dayAnchor(LocalDate.of(2026,month,2)),TimePrecision.DAY,"店",provider,"scope",null,""),true);var repo=new StatsRepository(db,Runnable::run);var pair=ComparisonWindows.monthToDate(Clock.fixed(Instant.parse("2026-11-06T04:00:00Z"),ZoneOffset.UTC)).get();var scope=new LedgerFilter(0,1,LedgerFilter.Metric.NET_OUTFLOW,LedgerTypes.UNCATEGORIZED_EXPENSE,"WECHAT");assertEquals(100,repo.compare(scope,pair).deltaFen);}finally{db.close();}}
}
