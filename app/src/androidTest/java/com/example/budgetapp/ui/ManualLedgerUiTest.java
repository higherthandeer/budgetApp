package com.example.budgetapp.ui;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ApplicationProvider;
import com.example.budgetapp.*;
import com.example.budgetapp.ui.ledger.LedgerViewModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicReference;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class ManualLedgerUiTest {
 @Test public void saveEditDeleteUndoSurvivesRecreation() throws Exception {
  BudgetApplication app=ApplicationProvider.getApplicationContext();
  assertTrue(app.getPackageName().endsWith(".testing"));
  app.container().io.submit(()->{app.container().db.clearAllTables();app.container().db.runInTransaction(()->com.example.budgetapp.data.LedgerDatabase.seedDefaults(app.container().db.getOpenHelper().getWritableDatabase()));}).get();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   AtomicReference<LedgerViewModel> vm=new AtomicReference<>();scenario.onActivity(a->{a.showLedger(a.ledgerViewModel().filter());vm.set(a.ledgerViewModel());});
   onView(withId(R.id.nav_add)).perform(click());UiTestSupport.awaitDisplayed(R.id.entry_amount);
   onView(withId(R.id.entry_amount)).perform(replaceText("28.80"),closeSoftKeyboard());scenario.recreate();
   onView(withId(R.id.entry_amount)).check(matches(withText("28.80")));
   onView(withId(R.id.entry_save)).perform(scrollTo(),click());
   UiTestSupport.awaitValue(vm.get().rows(),r->r!=null&&r.size()==1&&r.get(0).amountMinor==2880);
   scenario.recreate();scenario.onActivity(a->vm.set(a.ledgerViewModel()));
   UiTestSupport.settle();onView(org.hamcrest.Matchers.allOf(withId(R.id.row_amount),withText("−¥ 28.80"))).perform(click());UiTestSupport.awaitDisplayed(R.id.entry_amount);UiTestSupport.awaitValue(vm.get().busy(),b->Boolean.FALSE.equals(b));
   onView(withId(R.id.entry_amount)).perform(replaceText("18.80"),closeSoftKeyboard());onView(withId(R.id.entry_save)).perform(scrollTo(),click());
   UiTestSupport.awaitValue(vm.get().rows(),r->r!=null&&r.size()==1&&r.get(0).amountMinor==1880);
   UiTestSupport.settle();onView(org.hamcrest.Matchers.allOf(withId(R.id.row_amount),withText("−¥ 18.80"))).perform(click());UiTestSupport.awaitDisplayed(R.id.entry_amount);UiTestSupport.awaitValue(vm.get().busy(),b->Boolean.FALSE.equals(b));onView(withId(R.id.entry_delete)).perform(scrollTo(),click());onView(withText(R.string.delete_confirm)).perform(click());
   UiTestSupport.awaitValue(vm.get().rows(),r->r!=null&&r.isEmpty());
   onView(withId(R.id.ledger_undo)).perform(click());UiTestSupport.awaitValue(vm.get().rows(),r->r!=null&&r.size()==1&&r.get(0).amountMinor==1880);
  }
 }
 @Test public void settingsBackReturnsToLedger() {
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   onView(withId(R.id.ledger_settings)).perform(click());
   scenario.onActivity(a->a.getOnBackPressedDispatcher().onBackPressed());
   onView(withId(R.id.overview_expense)).check(matches(isDisplayed()));
  }
 }
}
