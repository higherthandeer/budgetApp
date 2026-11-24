package com.example.budgetapp.ui;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.*;
import com.example.budgetapp.data.LedgerDatabase;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.ui.overview.OverviewViewModel;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class) public class OverviewBalanceUiTest {
 @Test public void currentBalanceIncludesOpeningAndStaysIndependentOfSelectedMonth() throws Exception {
  BudgetApplication app=ApplicationProvider.getApplicationContext();assertTrue(app.getPackageName().endsWith(".testing"));
  var prefs=app.getSharedPreferences("local_settings",0);long previous=prefs.getLong("opening_balance_fen",0);
  YearMonth month=YearMonth.now(app.container().clock.withZone(TimeWindows.LEDGER_ZONE));
  long current=TimeWindows.month(month).startMs+1,old=TimeWindows.month(month.minusMonths(1)).startMs+1;
  prefs.edit().putLong("opening_balance_fen",100000).commit();
  String expense=app.container().io.submit(()->{app.container().db.clearAllTables();LedgerDatabase.seedDefaults(app.container().db.getOpenHelper().getWritableDatabase());
   app.container().repository.save("old-income",new TransactionDraft(Kind.INCOME,20000,old,TimePrecision.SECOND,"往月收入","WECHAT","scope",null,""),true);
   app.container().repository.save("old-expense",new TransactionDraft(Kind.EXPENSE,5000,old,TimePrecision.SECOND,"往月支出","WECHAT","scope",null,""),true);
   app.container().repository.save("income",new TransactionDraft(Kind.INCOME,30000,current,TimePrecision.SECOND,"本月收入","OTHER","scope",null,""),true);
   return app.container().repository.save("expense",new TransactionDraft(Kind.EXPENSE,4000,current,TimePrecision.SECOND,"本月支出","OTHER","scope",null,""),true).transactionId;
  }).get();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   AtomicReference<OverviewViewModel> vm=new AtomicReference<>();scenario.onActivity(a->vm.set(a.overviewViewModel()));
   UiTestSupport.awaitValue(vm.get().snapshot(),s->s.valid&&s.expenseFen==4000);
   int balanceId=app.getResources().getIdentifier("overview_balance","id",app.getPackageName());assertTrue("Missing balance card",balanceId!=0);
   awaitText(scenario,balanceId,"1410.00");awaitText(scenario,R.id.overview_net,"260.00");capture("balance-overview");
   scenario.onActivity(a->vm.get().setMonth(month.minusMonths(1)));
   UiTestSupport.awaitValue(vm.get().snapshot(),s->s.valid&&s.expenseFen==5000);awaitText(scenario,balanceId,"1410.00");awaitText(scenario,R.id.overview_net,"150.00");
   prefs.edit().putLong("opening_balance_fen",200000).commit();awaitText(scenario,balanceId,"2410.00");
   app.container().io.submit(()->app.container().repository.delete(expense,1)).get();awaitText(scenario,balanceId,"2450.00");
   scenario.recreate();awaitText(scenario,balanceId,"2450.00");
   prefs.edit().putLong("opening_balance_fen",-200000).commit();awaitText(scenario,balanceId,"-1550.00");
   prefs.edit().putLong("opening_balance_fen",Long.MAX_VALUE).commit();awaitText(scenario,balanceId,app.getString(R.string.stats_unavailable));
   prefs.edit().putLong("opening_balance_fen",200000).commit();awaitText(scenario,balanceId,"2450.00");
   app.container().io.submit(()->app.container().repository.save("large-expense",new TransactionDraft(Kind.EXPENSE,200000,current,TimePrecision.SECOND,"较大支出","OTHER","scope",null,""),true)).get();
   scenario.onActivity(a->vm.get().setMonth(month));UiTestSupport.awaitValue(vm.get().snapshot(),v->v.valid&&v.expenseFen==200000);awaitText(scenario,R.id.overview_net,"-1700.00");awaitText(scenario,balanceId,"450.00");
  }finally{prefs.edit().putLong("opening_balance_fen",previous).commit();}
 }
 @Test public void openingBalanceSettingDoesNotCreateAnIncome() throws Exception {
  BudgetApplication app=ApplicationProvider.getApplicationContext();assertTrue(app.getPackageName().endsWith(".testing"));long previous=app.container().settings.openingBalanceFen();
  app.container().io.submit(()->{app.container().db.clearAllTables();LedgerDatabase.seedDefaults(app.container().db.getOpenHelper().getWritableDatabase());}).get();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(MainActivity::showSettings);UiTestSupport.awaitView(scenario,R.id.settings_advanced);
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.opening_balance_title)).perform(androidx.test.espresso.action.ViewActions.scrollTo(),androidx.test.espresso.action.ViewActions.click());
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.EditText.class)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.replaceText("-123.45"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.category_apply)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click());
   UiTestSupport.awaitValue(app.container().settings.observeOpeningBalanceFen(),value->value==-12345L);
   AtomicReference<OverviewViewModel> home=new AtomicReference<>();scenario.onActivity(a->{a.showOverview();home.set(a.overviewViewModel());});
   UiTestSupport.awaitValue(home.get().snapshot(),value->value.valid&&value.incomeFen==0&&value.expenseFen==0);
   awaitText(scenario,R.id.overview_balance,"-123.45");assertEquals(0,(int)app.container().io.submit(()->app.container().db.ledgerDao().countPosted()).get());
  }finally{app.container().settings.setOpeningBalanceFen(previous);}
 }
 private static void capture(String name) throws Exception {
  var instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();instrumentation.waitForIdleSync();
  var bitmap=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(bitmap);
  var file=new java.io.File(instrumentation.getTargetContext().getExternalFilesDir(null),name+".png");
  try(var out=new java.io.FileOutputStream(file)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
  try(var fd=instrumentation.getUiAutomation().executeShellCommand("cp "+file.getAbsolutePath()+" /sdcard/Download/"+name+".png");var stream=new java.io.FileInputStream(fd.getFileDescriptor())){stream.readAllBytes();}
 }
 private static void awaitText(ActivityScenario<MainActivity> scenario,int id,String value) throws Exception {
  long until=android.os.SystemClock.uptimeMillis()+10000;AtomicReference<String> text=new AtomicReference<>("");
  while(android.os.SystemClock.uptimeMillis()<until){scenario.onActivity(a->{android.widget.TextView view=a.findViewById(id);text.set(view==null?"":view.getText().toString());});if(text.get().contains(value))return;android.os.SystemClock.sleep(80);}
  assertTrue("Expected "+value+", got "+text.get(),text.get().contains(value));
 }
}
