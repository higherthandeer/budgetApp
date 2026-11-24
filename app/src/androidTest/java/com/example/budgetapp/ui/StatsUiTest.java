package com.example.budgetapp.ui;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.*;
import com.example.budgetapp.data.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.ui.stats.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class StatsUiTest {
 @Test public void chartRowsDrillDownAndScopeSurvivesRecreation() throws Exception {
  BudgetApplication app=ApplicationProvider.getApplicationContext();assertTrue(app.getPackageName().endsWith(".testing"));YearMonth month=YearMonth.now(app.container().clock.withZone(TimeWindows.LEDGER_ZONE));long at=TimeWindows.month(month).startMs+1;
  app.container().io.submit(()->{app.container().db.clearAllTables();LedgerDatabase.seedDefaults(app.container().db.getOpenHelper().getWritableDatabase());app.container().repository.save("food",new TransactionDraft(Kind.EXPENSE,2880,at,TimePrecision.SECOND,"面馆","WECHAT","scope","00000000-0000-0000-0000-000000000003",""),true);}).get();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   AtomicReference<StatsViewModel> vm=new AtomicReference<>();scenario.onActivity(a->{a.showStats();vm.set(a.statsViewModel());vm.get().setScope(null,"WECHAT");});UiTestSupport.awaitValue(vm.get().display(),d->d.snapshot.expenseFen==2880&&d.trend.size()==6&&"WECHAT".equals(d.snapshot.filter.provider));onView(withId(R.id.stats_twelve)).perform(scrollTo(),click());scenario.recreate();scenario.onActivity(a->vm.set(a.statsViewModel()));assertEquals(12,vm.get().monthCount());assertEquals("WECHAT",vm.get().provider());UiTestSupport.awaitValue(vm.get().display(),d->d.trend.size()==12);app.container().io.submit(()->{}).get();androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();onView(allOf(withId(R.id.stats_value),withText(containsString("餐饮")))).perform(scrollTo(),click());UiTestSupport.awaitView(scenario,R.id.ledger_list);scenario.onActivity(a->{assertEquals("00000000-0000-0000-0000-000000000003",a.ledgerViewModel().filter().categoryId);assertEquals("WECHAT",a.ledgerViewModel().filter().provider);});
   scenario.onActivity(MainActivity::showStats);UiTestSupport.awaitValue(vm.get().display(),d->d.trend.size()==12);onView(withId(R.id.stats_chart_help)).perform(scrollTo(),click());onView(allOf(withId(R.id.stats_value),withText(startsWith(month.toString())))).perform(scrollTo(),click());scenario.onActivity(a->{assertEquals(TimeWindows.month(month).startMs,a.ledgerViewModel().filter().startMs);assertEquals("WECHAT",a.ledgerViewModel().filter().provider);});
   scenario.onActivity(a->{a.showStats();vm.get().setCustomDates(month.atDay(1),month.atEndOfMonth());});var custom=UiTestSupport.awaitValue(vm.get().display(),d->d.snapshot.filter.endMs==TimeWindows.month(month).endMs);assertEquals(2880,custom.snapshot.valueFen);scenario.onActivity(a->vm.get().setMetric(LedgerFilter.Metric.INCOME));UiTestSupport.awaitValue(vm.get().display(),d->d.snapshot.filter.metric==LedgerFilter.Metric.INCOME);onView(withId(R.id.stats_category_section)).check(androidx.test.espresso.assertion.ViewAssertions.matches(withEffectiveVisibility(Visibility.VISIBLE)));
  }
 }
 @Test public void chartTouchSelectsOnlyAnActualBar(){try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(a->{BudgetChartView chart=new BudgetChartView(a);LedgerFilter first=new LedgerFilter(0,1,LedgerFilter.Metric.EXPENSE,null,null),second=new LedgerFilter(1,2,LedgerFilter.Metric.EXPENSE,null,null);AtomicReference<LedgerFilter> selected=new AtomicReference<>();AtomicInteger calls=new AtomicInteger();chart.setItems(Arrays.asList(new BudgetChartView.ChartItem("a",100,first),new BudgetChartView.ChartItem("b",200,second)),filter->{selected.set(filter);calls.incrementAndGet();});chart.layout(0,0,600,400);touch(chart,450,200);assertSame(second,selected.get());touch(chart,0,0);assertEquals(1,calls.get());});}}
 private void touch(BudgetChartView chart,float x,float y){long now=android.os.SystemClock.uptimeMillis();for(int action:new int[]{android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP}){android.view.MotionEvent event=android.view.MotionEvent.obtain(now,now,action,x,y,0);chart.dispatchTouchEvent(event);event.recycle();}}
}
