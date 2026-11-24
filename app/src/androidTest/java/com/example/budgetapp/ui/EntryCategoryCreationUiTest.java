package com.example.budgetapp.ui;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.*;
import com.example.budgetapp.data.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.ui.ledger.LedgerViewModel;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
@RunWith(AndroidJUnit4.class) public class EntryCategoryCreationUiTest {
 private BudgetApplication app;
 @Before public void setup() throws Exception {app=ApplicationProvider.getApplicationContext();assertTrue(app.getPackageName().endsWith(".testing"));app.container().io.submit(()->{app.container().db.clearAllTables();LedgerDatabase.seedDefaults(app.container().db.getOpenHelper().getWritableDatabase());}).get();}
 private int addId(){int id=app.getResources().getIdentifier("entry_category_add","id",app.getPackageName());assertTrue("Missing in-editor category action",id!=0);return id;}
 private LedgerViewModel open(ActivityScenario<MainActivity> s,String tx) throws Exception {AtomicReference<LedgerViewModel> vm=new AtomicReference<>();s.onActivity(a->{a.showEditor(tx);vm.set(a.ledgerViewModel());});UiTestSupport.awaitValue(vm.get().categoryChoices(),c->c!=null);UiTestSupport.settle();return vm.get();}
 private void add(String name){onView(withId(addId())).inRoot(isDialog()).perform(scrollTo(),click());onView(withId(R.id.category_name_input)).inRoot(isDialog()).perform(replaceText(name),closeSoftKeyboard());}
 @Test public void expenseCreationSelectsIconAndPreservesUnsavedBill() throws Exception {
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   var vm=open(s,null);onView(withId(R.id.entry_amount)).inRoot(isDialog()).perform(replaceText("28.80"),closeSoftKeyboard());onView(withId(R.id.entry_merchant)).inRoot(isDialog()).perform(scrollTo(),replaceText("水果店"),closeSoftKeyboard());onView(withId(R.id.entry_note)).inRoot(isDialog()).perform(scrollTo(),replaceText("保留草稿"),closeSoftKeyboard());
   add("随手新建");onView(withId(R.id.category_flow)).inRoot(isDialog()).check(matches(withEffectiveVisibility(Visibility.GONE)));
   onView(withId(R.id.category_icon_choose)).inRoot(isDialog()).perform(click());onView(withHint("搜索图标，如咖啡、交通")).inRoot(isDialog()).perform(replaceText("水果"),closeSoftKeyboard());onView(withContentDescription("图标：水果")).inRoot(isDialog()).perform(click());onView(withText("使用此图标")).inRoot(isDialog()).perform(click());onView(withText("创建并使用")).inRoot(isDialog()).perform(click());
   var choices=UiTestSupport.awaitValue(vm.categoryChoices(),c->c!=null&&c.items.stream().anyMatch(i->i.name.equals("随手新建")&&i.id.equals(c.selectedId)));
   var category=choices.items.stream().filter(i->i.id.equals(choices.selectedId)).findFirst().get();assertEquals("EXPENSE",category.flowScope);assertEquals("nutrition",category.iconKey);
   assertEquals("28.80",vm.field("amount",""));assertEquals("水果店",vm.field("merchant",""));assertEquals("保留草稿",vm.field("note",""));assertEquals(0,(int)app.container().io.submit(()->app.container().db.ledgerDao().countPosted()).get());
   onView(withId(R.id.entry_save)).inRoot(isDialog()).perform(scrollTo(),click());var rows=UiTestSupport.awaitValue(vm.rows(),r->r!=null&&r.size()==1);assertEquals(category.id,rows.get(0).categoryId);assertEquals(2880,rows.get(0).amountMinor);
  }
 }
 @Test public void incomeEditKeepsTypeAndNewCategoryDraftAcrossRecreation() throws Exception {
  String id=app.container().io.submit(()->app.container().repository.save("income-before",new TransactionDraft(Kind.INCOME,10000,1000,TimePrecision.SECOND,"公司","OTHER","scope",null,"原备注"),true).transactionId).get();
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   var vm=open(s,id);add("新收入类别");s.recreate();UiTestSupport.settle();onView(withId(R.id.category_name_input)).inRoot(isDialog()).check(matches(withText("新收入类别")));onView(withText("新增收入分类")).inRoot(isDialog()).check(matches(isDisplayed()));onView(withText("创建并使用")).inRoot(isDialog()).perform(click());
   var choices=UiTestSupport.awaitValue(vm.categoryChoices(),c->c!=null&&c.items.stream().anyMatch(i->i.name.equals("新收入类别")&&i.id.equals(c.selectedId)));var category=choices.items.stream().filter(i->i.id.equals(choices.selectedId)).findFirst().get();assertEquals("INCOME",category.flowScope);assertEquals("100.00",vm.field("amount",""));assertEquals("原备注",vm.field("note",""));
   onView(withId(R.id.entry_save)).inRoot(isDialog()).perform(scrollTo(),click());UiTestSupport.awaitValue(vm.rows(),r->r!=null&&r.stream().anyMatch(t->t.id.equals(id)&&t.revision==2));var tx=app.container().io.submit(()->app.container().db.ledgerDao().find(id)).get();assertEquals(category.id,tx.categoryId);assertEquals("INCOME",tx.kind);assertEquals(1000,tx.occurredAtMs);
  }
 }
 @Test public void duplicateAndCancelLeaveSelectionAndAmountUntouched() throws Exception {
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   var vm=open(s,null);onView(withId(R.id.entry_amount)).inRoot(isDialog()).perform(replaceText("9.90"),closeSoftKeyboard());onView(withContentDescription("餐饮")).inRoot(isDialog()).perform(scrollTo(),click());var original=UiTestSupport.awaitValue(vm.categoryChoices(),c->c!=null&&c.selectedId.endsWith("000003")).selectedId;
   add("餐饮");onView(withText("创建并使用")).inRoot(isDialog()).perform(click());UiTestSupport.settle();onView(withId(R.id.category_name_input)).inRoot(isDialog()).check(matches(hasErrorText(app.getString(R.string.category_name_exists))));onView(withText(R.string.entry_cancel)).inRoot(isDialog()).perform(click());assertEquals("9.90",vm.field("amount",""));assertEquals(original,vm.categoryChoices().getValue().selectedId);assertEquals(5,(int)app.container().io.submit(()->app.container().categories.categories().size()).get());
  }
 }
 @Test public void queuedCreationSurvivesRecreationWithoutSavingTheBill() throws Exception {
  java.util.concurrent.CountDownLatch queued=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   var vm=open(scenario,null);onView(withId(R.id.entry_amount)).inRoot(isDialog()).perform(replaceText("3.14"),closeSoftKeyboard());add("异步恢复分类");
   app.container().io.execute(()->{queued.countDown();try{release.await(20,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
   assertTrue(queued.await(10,java.util.concurrent.TimeUnit.SECONDS));onView(withText("创建并使用")).inRoot(isDialog()).perform(click());scenario.recreate();
   onView(withText("创建并使用")).inRoot(isDialog()).check(matches(org.hamcrest.Matchers.not(isEnabled())));
   release.countDown();var choices=UiTestSupport.awaitValue(vm.categoryChoices(),c->c!=null&&c.items.stream().anyMatch(i->i.name.equals("异步恢复分类")&&i.id.equals(c.selectedId)));
   assertEquals("3.14",vm.field("amount",""));assertEquals(1L,(long)app.container().io.submit(()->app.container().categories.categories().stream().filter(c->c.name.equals("异步恢复分类")).count()).get());assertEquals(0,(int)app.container().io.submit(()->app.container().db.ledgerDao().countPosted()).get());
   UiTestSupport.settle();AtomicReference<Boolean> closed=new AtomicReference<>();scenario.onActivity(a->closed.set(((com.example.budgetapp.ui.ledger.EntryEditorFragment)a.getSupportFragmentManager().findFragmentByTag("entry_editor_sheet")).getChildFragmentManager().findFragmentByTag("entry_category_create")==null));assertTrue(closed.get());
  } finally {release.countDown();app.container().io.submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);}
 }

}
