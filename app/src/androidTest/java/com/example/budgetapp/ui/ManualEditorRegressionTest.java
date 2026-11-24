package com.example.budgetapp.ui;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.*;
import com.example.budgetapp.data.*;
import com.example.budgetapp.data.entity.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.ui.ledger.LedgerViewModel;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ManualEditorRegressionTest {
 private BudgetApplication app;
 private static final String FOOD="00000000-0000-0000-0000-000000000003";
 @Before public void setUp() throws Exception {
  app=ApplicationProvider.getApplicationContext();assertTrue(app.getPackageName().endsWith(".testing"));
  app.container().io.submit(()->{app.container().db.clearAllTables();LedgerDatabase.seedDefaults(app.container().db.getOpenHelper().getWritableDatabase());}).get(10,TimeUnit.SECONDS);
 }
 private TransactionDraft draft(long amount,long at,String merchant,String scope,String note){return new TransactionDraft(Kind.EXPENSE,amount,at,TimePrecision.SECOND,merchant,"WECHAT",scope,null,note);}
 @Test public void noteOnlyEditPreservesTimestampPrecisionAndHistoricalScope() throws Exception {
  long at=Instant.parse("2026-11-05T04:34:56Z").toEpochMilli();String scope=app.container().settings.accountScope("WECHAT");
  String id=app.container().io.submit(()->app.container().repository.save("precise",draft(2880,at,"精确时间店",scope,"原备注"),true).transactionId).get();
  app.container().settings.replaceAccountScope("WECHAT","新的测试范围");
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   AtomicReference<LedgerViewModel> vm=new AtomicReference<>();scenario.onActivity(a->{a.showEditor(id);vm.set(a.ledgerViewModel());});UiTestSupport.awaitValue(vm.get().editing,t->t!=null&&t.id.equals(id));scenario.recreate();
   onView(withId(R.id.entry_note)).perform(scrollTo(),replaceText("仅修改备注"),closeSoftKeyboard());onView(withId(R.id.entry_save)).perform(scrollTo(),click());UiTestSupport.awaitValue(vm.get().rows(),rows->rows!=null&&rows.stream().anyMatch(t->t.id.equals(id)&&t.revision==2));
   var edited=app.container().io.submit(()->app.container().db.ledgerDao().find(id)).get();assertEquals(at,edited.occurredAtMs);assertEquals("SECOND",edited.timePrecision);assertEquals(scope,edited.accountScopeId);assertEquals("仅修改备注",edited.note);
  }
 }
 @Test public void rememberedMerchantRuleAppliesWithoutCategoryOverride() throws Exception {
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   AtomicReference<LedgerViewModel> vm=new AtomicReference<>();scenario.onActivity(a->{a.showLedger(a.ledgerViewModel().filter());vm.set(a.ledgerViewModel());});
   UiTestSupport.settle();onView(withId(R.id.nav_add)).perform(click());UiTestSupport.awaitDisplayed(R.id.entry_amount);onView(withId(R.id.entry_amount)).perform(replaceText("28.80"));onView(withId(R.id.entry_merchant)).perform(scrollTo(),replaceText("回归面馆"),closeSoftKeyboard());UiTestSupport.awaitValue(vm.get().categoryChoices(),c->c!=null&&c.items.size()>=2);onView(withId(R.id.entry_category)).check(matches(isEnabled()));onView(withText("餐饮")).perform(scrollTo(),click());onView(withId(R.id.entry_rule)).perform(scrollTo(),click());onView(withId(R.id.entry_save)).perform(scrollTo(),click());UiTestSupport.awaitValue(vm.get().rows(),rows->rows!=null&&rows.size()==1);
   UiTestSupport.awaitView(scenario,R.id.nav_add);UiTestSupport.settle();onView(withId(R.id.nav_add)).perform(click());UiTestSupport.awaitDisplayed(R.id.entry_amount);onView(withId(R.id.entry_amount)).perform(replaceText("9.90"));onView(withId(R.id.entry_merchant)).perform(scrollTo(),replaceText("回归面馆"),closeSoftKeyboard());onView(withId(R.id.entry_rule)).perform(scrollTo(),click());onView(withId(R.id.entry_save)).perform(scrollTo(),click());
   var rows=UiTestSupport.awaitValue(vm.get().rows(),r->r!=null&&r.size()==2);var added=rows.stream().filter(t->t.amountMinor==990).findFirst().get();assertEquals(FOOD,added.categoryId);assertEquals("RULE",added.categoryOrigin);assertFalse(added.userLockedFields.contains("category"));assertTrue(app.container().io.submit(()->app.container().categories.rules().stream().allMatch(r->r.categoryId.equals(FOOD))).get());
  }
 }
 @Test public void pickerUsesRenamedCategoriesAndExcludesArchivedAssignments() throws Exception {
  app.container().io.submit(()->{app.container().categories.renameCategory(FOOD,"外食回归");app.container().categories.archiveCategory("00000000-0000-0000-0000-000000000004");}).get();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   AtomicReference<LedgerViewModel> vm=new AtomicReference<>();scenario.onActivity(a->{a.showEditor(null);vm.set(a.ledgerViewModel());});UiTestSupport.awaitValue(vm.get().categoryChoices(),c->c!=null&&c.items.stream().anyMatch(item->item.name.equals("外食回归")));
   onView(withId(R.id.entry_amount)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(replaceText("1.00"),closeSoftKeyboard());
   onView(withId(R.id.entry_category)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isEnabled()));
   onView(org.hamcrest.Matchers.allOf(withText("交通"),isDescendantOfA(withId(R.id.entry_category)))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(doesNotExist());
   onView(withText("外食回归")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(scrollTo(),click());
  }
 }
 @Test public void obsoleteLoadCannotPopulateANewerEditor() throws Exception {
  String scope=app.container().settings.accountScope("WECHAT");String[] ids=app.container().io.submit(()->new String[]{app.container().repository.save("a",draft(100,100,"A",scope,"A note"),true).transactionId,app.container().repository.save("b",draft(200,200,"B",scope,"B note"),true).transactionId}).get();CountDownLatch blocked=new CountDownLatch(1),release=new CountDownLatch(1);
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   AtomicReference<LedgerViewModel> vm=new AtomicReference<>();scenario.onActivity(a->{vm.set(a.ledgerViewModel());vm.get().beginEditor(ids[0]);app.container().io.execute(()->{blocked.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});vm.get().beginEditor(ids[1]);});assertTrue(blocked.await(10,TimeUnit.SECONDS));AtomicReference<String> note=new AtomicReference<>();scenario.onActivity(a->note.set(vm.get().field("note","")));assertNotEquals("A note",note.get());release.countDown();UiTestSupport.awaitValue(vm.get().editing,t->t!=null&&t.id.equals(ids[1]));assertEquals("B note",vm.get().field("note",""));
  }finally{release.countDown();app.container().io.submit(()->{}).get(10,TimeUnit.SECONDS);}
 }
 @Test public void obsoleteSaveCannotCloseNewDraftOrReadItsRuleChoice() throws Exception {
  CountDownLatch blocked=new CountDownLatch(1),release=new CountDownLatch(1);
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   AtomicReference<LedgerViewModel> vm=new AtomicReference<>();app.container().io.execute(()->{blocked.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(blocked.await(10,TimeUnit.SECONDS));scenario.onActivity(a->{vm.set(a.ledgerViewModel());vm.get().beginEditor(null);vm.get().field("amount","1.00",true);vm.get().field("merchant","A",true);vm.get().save(false);vm.get().beginEditor(null);vm.get().field("amount","2.00",true);vm.get().field("merchant","B",true);vm.get().field("makeRule","true",true);});release.countDown();app.container().io.submit(()->{}).get(10,TimeUnit.SECONDS);AtomicReference<Boolean> closed=new AtomicReference<>();scenario.onActivity(a->closed.set(vm.get().closed.getValue()));assertFalse(Boolean.TRUE.equals(closed.get()));assertTrue(app.container().io.submit(()->app.container().categories.rules().isEmpty()).get());
  }finally{release.countDown();app.container().io.submit(()->{}).get(10,TimeUnit.SECONDS);}
 }
}
