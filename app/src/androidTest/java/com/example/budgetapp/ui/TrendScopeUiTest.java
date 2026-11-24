package com.example.budgetapp.ui;

import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.*;
import com.example.budgetapp.data.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.ui.stats.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TrendScopeUiTest {
 @Test public void selectingTrendCategoryKeepsWholeMonthTotal() throws Exception {
  BudgetApplication app=ApplicationProvider.getApplicationContext();
  assertTrue(app.getPackageName().endsWith(".testing"));
  YearMonth month=YearMonth.now(app.container().clock.withZone(TimeWindows.LEDGER_ZONE));
  long at=TimeWindows.month(month).startMs+1;
  String food="00000000-0000-0000-0000-000000000003";
  app.container().io.submit(()->{
   app.container().db.clearAllTables();
   LedgerDatabase.seedDefaults(app.container().db.getOpenHelper().getWritableDatabase());
   app.container().repository.save("food",new TransactionDraft(Kind.EXPENSE,10000,at,TimePrecision.SECOND,"面馆","WECHAT","scope",food,""),true);
   app.container().repository.save("other",new TransactionDraft(Kind.EXPENSE,20000,at,TimePrecision.SECOND,"其他","WECHAT","scope","00000000-0000-0000-0000-000000000001",""),true);
  }).get();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   AtomicReference<StatsViewModel> vm=new AtomicReference<>();
   scenario.onActivity(a->{a.showStats();vm.set(a.statsViewModel());});
   UiTestSupport.awaitValue(vm.get().display(),d->d.snapshot.expenseFen==30000);
   scenario.onActivity(a->vm.get().setScope(food,null));
   var selected=UiTestSupport.awaitValue(vm.get().display(),d->d.trend.stream().anyMatch(p->food.equals(p.filter.categoryId)));
   assertEquals(30000,selected.snapshot.expenseFen);
   assertEquals(2,selected.snapshot.categories.size());
   assertEquals(10000,selected.trend.get(selected.trend.size()-1).valueFen);
  }
 }
}
