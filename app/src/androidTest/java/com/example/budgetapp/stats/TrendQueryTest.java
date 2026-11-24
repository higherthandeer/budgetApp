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
@RunWith(AndroidJUnit4.class) public class TrendQueryTest {
 @Test public void sixAndTwelveMonthsZeroFillAndKeepDrilldownExact(){var db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),LedgerDatabase.class).addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();try{Clock clock=Clock.fixed(Instant.parse("2026-01-15T04:00:00Z"),ZoneOffset.UTC);var manual=new ManualLedgerRepository(db,clock);manual.save("past",new TransactionDraft(Kind.EXPENSE,100,TimeWindows.dayAnchor(LocalDate.of(2025,12,31)),TimePrecision.DAY,"店","WECHAT","scope",null,""),true);manual.save("future",new TransactionDraft(Kind.EXPENSE,999,TimeWindows.dayAnchor(LocalDate.of(2026,1,20)),TimePrecision.DAY,"店","WECHAT","scope",null,""),true);var repo=new StatsRepository(db,Runnable::run);var scope=new LedgerFilter(0,1,LedgerFilter.Metric.EXPENSE,null,"WECHAT");var points=repo.trend(scope,YearMonth.of(2026,1),6,clock);assertEquals(6,points.size());assertEquals(YearMonth.of(2025,8),points.get(0).month);assertEquals(100,points.get(4).valueFen);assertEquals(0,points.get(5).valueFen);assertTrue(points.get(5).inProgress);for(var p:points)assertEquals(p.valueFen,repo.load(p.filter).valueFen);assertEquals(12,repo.trend(scope,YearMonth.of(2026,1),12,clock).size());assertThrows(IllegalArgumentException.class,()->repo.trend(scope,YearMonth.of(2026,1),3,clock));var custom=TimeWindows.inclusiveDates(LocalDate.of(2025,12,31),LocalDate.of(2026,1,20));assertEquals(1099,repo.load(scope.withRange(custom.startMs,custom.endMs)).valueFen);}finally{db.close();}}
}
