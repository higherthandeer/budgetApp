package com.example.budgetapp.ui;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.*;
import com.example.budgetapp.data.*;
import com.example.budgetapp.data.entity.LedgerTransactionEntity;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.ui.ledger.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
@RunWith(AndroidJUnit4.class) public class LedgerInteractionUiTest {
 @Test public void incomeAlwaysUsesPlusAndDifferentColorEvenInAllBills(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{var row=a.getLayoutInflater().inflate(R.layout.item_ledger,null,false);var income=new LedgerTransactionEntity();income.kind="INCOME";income.categoryId=LedgerTypes.UNCATEGORIZED_INCOME;income.amountMinor=5000;
    LedgerAdapter.bindTransaction(row,income,true);android.widget.TextView amount=row.findViewById(R.id.row_amount);assertEquals("+¥ 50.00",amount.getText().toString());int incomeColor=amount.getCurrentTextColor();income.kind="EXPENSE";LedgerAdapter.bindTransaction(row,income,true);assertTrue(amount.getText().toString().startsWith("−"));assertNotEquals(incomeColor,amount.getCurrentTextColor());
   });
  }
 }
 @Test public void billTapDirectlyEditsAndFiltersFollowMonth() throws Exception {
  BudgetApplication app=ApplicationProvider.getApplicationContext();assertTrue(app.getPackageName().endsWith(".testing"));long at=TimeWindows.month(YearMonth.now(app.container().clock.withZone(TimeWindows.LEDGER_ZONE))).startMs+1;
  app.container().io.submit(()->{app.container().db.clearAllTables();LedgerDatabase.seedDefaults(app.container().db.getOpenHelper().getWritableDatabase());app.container().repository.save("editor-click",new TransactionDraft(Kind.EXPENSE,1000,at,TimePrecision.SECOND,"文具测试","OTHER","scope",null,""),true);}).get();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   AtomicReference<LedgerViewModel> vm=new AtomicReference<>();scenario.onActivity(a->{a.showLedger(a.ledgerViewModel().filter());vm.set(a.ledgerViewModel());});UiTestSupport.awaitValue(vm.get().rows(),r->r.size()==1);UiTestSupport.awaitDisplayed(R.id.row_merchant);
   scenario.onActivity(a->{int[] month=new int[2],category=new int[2],total=new int[2];a.findViewById(R.id.ledger_month).getLocationOnScreen(month);a.findViewById(R.id.ledger_category).getLocationOnScreen(category);a.findViewById(R.id.ledger_total).getLocationOnScreen(total);assertTrue("Month before filters",month[1]<category[1]);assertTrue("Filters before summary",category[1]<total[1]);});
   onView(withId(R.id.row_merchant)).perform(click());UiTestSupport.awaitDisplayed(R.id.entry_amount);onView(withId(R.id.entry_amount)).check(matches(withText("10.00")));onView(withId(R.id.entry_delete)).perform(scrollTo()).check(matches(isDisplayed()));
   scenario.onActivity(a->{var editor=(androidx.fragment.app.DialogFragment)a.getSupportFragmentManager().findFragmentByTag(EntryEditorFragment.TAG);editor.dismissNow();a.showEditor(null);});onView(withId(R.id.entry_delete)).check(matches(withEffectiveVisibility(Visibility.GONE)));
  }
 }
 @Test public void partialMonthShowsActualRangeAndClear() throws Exception {
  BudgetApplication app=ApplicationProvider.getApplicationContext();YearMonth month=YearMonth.now(app.container().clock.withZone(TimeWindows.LEDGER_ZONE));long start=TimeWindows.month(month.minusMonths(1)).startMs;long end=month.minusMonths(1).atDay(16).atStartOfDay(TimeWindows.LEDGER_ZONE).toInstant().toEpochMilli();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   AtomicReference<LedgerViewModel> vm=new AtomicReference<>();scenario.onActivity(a->{vm.set(a.ledgerViewModel());a.showLedger(new LedgerFilter(start,end,LedgerFilter.Metric.NET_OUTFLOW,null,null));});UiTestSupport.awaitValue(vm.get().snapshot(),v->v.filter.startMs==start&&v.filter.endMs==end);UiTestSupport.awaitDisplayed(R.id.ledger_clear);onView(withId(R.id.ledger_month)).check(matches(withText(org.hamcrest.Matchers.containsString("15"))));
  }
 }
}
