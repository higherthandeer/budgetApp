package com.example.budgetapp.ui;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.*;
import com.example.budgetapp.data.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.ui.overview.OverviewViewModel;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class OverviewUiTest {
 @Test public void totalsDrilldownAndUpdatesKeepIdenticalScope() throws Exception {
  BudgetApplication app=ApplicationProvider.getApplicationContext();assertTrue(app.getPackageName().endsWith(".testing"));long date=TimeWindows.month(YearMonth.now(app.container().clock.withZone(TimeWindows.LEDGER_ZONE))).startMs+1;
  String tx=app.container().io.submit(()->{app.container().db.clearAllTables();LedgerDatabase.seedDefaults(app.container().db.getOpenHelper().getWritableDatabase());String id=app.container().repository.save("expense",new TransactionDraft(Kind.EXPENSE,2880,date,TimePrecision.SECOND,"面馆","WECHAT","scope",null,""),true).transactionId;app.container().repository.save("income",new TransactionDraft(Kind.INCOME,300000,date,TimePrecision.SECOND,"工资","WECHAT","scope",null,""),true);return id;}).get();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   AtomicReference<OverviewViewModel> vm=new AtomicReference<>();scenario.onActivity(a->{a.showOverview();vm.set(a.overviewViewModel());vm.get().setScope(null,"WECHAT");});
   for(int index=0;index<2;index++){if(index>0)scenario.onActivity(MainActivity::showOverview);var snapshot=UiTestSupport.awaitValue(vm.get().snapshot(),s->s.valid&&s.expenseFen==2880&&s.incomeFen==300000);int id=new int[]{R.id.overview_expense,R.id.overview_income,R.id.overview_net}[index];UiTestSupport.awaitView(scenario,id);onView(withId(id)).perform(click());AtomicReference<com.example.budgetapp.ui.ledger.LedgerViewModel> ledger=new AtomicReference<>();scenario.onActivity(a->ledger.set(a.ledgerViewModel()));var metric=LedgerFilter.Metric.values()[index];assertEquals(metric,ledger.get().filter().metric);assertEquals("WECHAT",ledger.get().filter().provider);assertEquals(snapshot.filter.startMs,ledger.get().filter().startMs);var detail=UiTestSupport.awaitValue(ledger.get().snapshot(),s->s.valid&&s.filter.metric==metric);assertEquals(detail.valueFen,detail.entries.stream().mapToLong(e->e.contributionFen).sum());}
   scenario.onActivity(MainActivity::showOverview);app.container().io.submit(()->app.container().repository.update(tx,1,new TransactionDraft(Kind.EXPENSE,1880,date,TimePrecision.SECOND,"面馆","WECHAT","scope",null,""))).get();assertEquals(1880,UiTestSupport.awaitValue(vm.get().snapshot(),s->s.valid&&s.expenseFen==1880).expenseFen);
  }
 }
 @Test public void currentComparisonShowsCompleteDayScope() throws Exception {
  BudgetApplication app=ApplicationProvider.getApplicationContext();try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){AtomicReference<OverviewViewModel> vm=new AtomicReference<>();scenario.onActivity(a->{a.showOverview();vm.set(a.overviewViewModel());});var expected=com.example.budgetapp.stats.ComparisonWindows.monthToDate(app.container().clock);String marker=expected.isPresent()?"前 "+expected.get().comparedDays+" 天":"暂无完整日可比";var card=UiTestSupport.awaitValue(vm.get().comparison(),c->c!=null&&c.caption.contains(marker));assertTrue(card.caption.contains(marker));scenario.onActivity(a->vm.get().setMonth(vm.get().month().minusMonths(1)));assertTrue(UiTestSupport.awaitValue(vm.get().comparison(),c->c!=null&&c.caption.contains("整月环比")).caption.contains("整月环比"));}}
}
