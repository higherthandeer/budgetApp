package com.example.budgetapp.ui;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.*;
import com.example.budgetapp.data.LedgerDatabase;
import com.example.budgetapp.data.entity.CategoryEntity;
import com.example.budgetapp.ui.ledger.LedgerViewModel;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class) public class CategoryCreationUiTest {
 private BudgetApplication app;
 @Before public void setup() throws Exception {app=ApplicationProvider.getApplicationContext();assertTrue(app.getPackageName().endsWith(".testing"));app.container().io.submit(()->{app.container().db.clearAllTables();LedgerDatabase.seedDefaults(app.container().db.getOpenHelper().getWritableDatabase());}).get();}
 private int id(String name){int value=app.getResources().getIdentifier(name,"id",app.getPackageName());assertTrue("Missing category creation control: "+name,value!=0);return value;}
 private void add(String name,boolean income){onView(withId(id("category_add"))).perform(click());UiTestSupport.awaitDisplayed(id("category_name_input"));onView(withId(id("category_name_input"))).perform(replaceText(name),closeSoftKeyboard());if(income)onView(withId(id("category_flow_income"))).perform(click());onView(withId(android.R.id.button1)).perform(click());}
 private CategoryEntity awaitCategory(String name,String kind) throws Exception {long end=android.os.SystemClock.uptimeMillis()+10000;while(android.os.SystemClock.uptimeMillis()<end){var found=app.container().io.submit(()->app.container().categories.categories().stream().filter(c->name.equals(c.name)&&kind.equals(c.flowScope)).findFirst().orElse(null)).get();if(found!=null)return found;android.os.SystemClock.sleep(50);}throw new AssertionError("Category not saved");}
 @Test public void createsBothKindsAndNewIncomeCanBeBooked() throws Exception {
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(MainActivity::showCategoryRules);int add=id("category_add");UiTestSupport.awaitView(scenario,add);add("学习",false);awaitCategory("学习","EXPENSE");UiTestSupport.awaitDisplayed(add);add("兼职",true);var income=awaitCategory("兼职","INCOME");
   AtomicReference<LedgerViewModel> vm=new AtomicReference<>();scenario.onActivity(a->{vm.set(a.ledgerViewModel());a.showEditor(null);});
   UiTestSupport.awaitValue(vm.get().categoryChoices(),c->c!=null&&c.items.stream().anyMatch(item->item.name.equals("学习"))&&c.items.stream().noneMatch(item->item.id.equals(income.id)));
   onView(withId(R.id.entry_income)).perform(click());UiTestSupport.awaitValue(vm.get().categoryChoices(),c->c!=null&&c.items.stream().anyMatch(item->item.id.equals(income.id)));
   onView(withText("兼职")).perform(click());onView(withId(R.id.entry_amount)).perform(replaceText("100.00"),closeSoftKeyboard());onView(withId(R.id.entry_save)).perform(scrollTo(),click());
   UiTestSupport.awaitValue(vm.get().rows(),rows->rows.size()==1&&rows.get(0).categoryId.equals(income.id)&&rows.get(0).kind.equals("INCOME"));
  }
 }
 @Test public void cancellingDoesNotCreateCategory() throws Exception {
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(MainActivity::showCategoryRules);int add=id("category_add");UiTestSupport.awaitView(scenario,add);onView(withId(add)).perform(click());onView(withId(id("category_name_input"))).perform(replaceText("\u3000\u00a0"),closeSoftKeyboard());onView(withId(android.R.id.button1)).perform(click());onView(withId(id("category_name_input"))).check(androidx.test.espresso.assertion.ViewAssertions.matches(hasErrorText(app.getString(R.string.category_name_invalid))));onView(withId(id("category_name_input"))).perform(replaceText("取消的类别"),closeSoftKeyboard());onView(withId(android.R.id.button2)).perform(click());assertEquals(5,(int)app.container().io.submit(()->app.container().categories.categories().size()).get());
  }
 }
 @Test public void duplicateNameKeepsFormOpenAndDoesNotInsert() throws Exception {
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(MainActivity::showCategoryRules);int add=id("category_add");UiTestSupport.awaitView(scenario,add);add("餐饮",false);UiTestSupport.settle();
   onView(withId(id("category_name_input"))).check(androidx.test.espresso.assertion.ViewAssertions.matches(hasErrorText(app.getString(R.string.category_name_exists))));assertEquals(5,(int)app.container().io.submit(()->app.container().categories.categories().size()).get());
  }
 }
}
