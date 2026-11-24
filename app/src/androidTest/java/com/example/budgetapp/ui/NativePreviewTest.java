package com.example.budgetapp.ui;

import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.budgetapp.*;
import com.example.budgetapp.data.LedgerDatabase;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import java.time.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NativePreviewTest {
 @Test public void captureSixScreensAndPreserveNavigation() throws Exception {
  BudgetApplication app=ApplicationProvider.getApplicationContext();
  assertTrue(app.getPackageName().endsWith(".testing"));
  YearMonth month=YearMonth.now(app.container().clock.withZone(TimeWindows.LEDGER_ZONE));
  long at=TimeWindows.month(month).startMs+36000000;
  app.container().io.submit(()->{app.container().db.clearAllTables();LedgerDatabase.seedDefaults(app.container().db.getOpenHelper().getWritableDatabase());for(int i=0;i<4;i++)app.container().repository.save("preview-"+i,new TransactionDraft(Kind.EXPENSE,1800+i*1250,at+i*86400000L,TimePrecision.SECOND,new String[]{"午间面馆","便利店","地铁出行","周末午餐"}[i],"WECHAT","scope",i==2?"00000000-0000-0000-0000-000000000004":"00000000-0000-0000-0000-000000000003",""),true);}).get();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   java.util.concurrent.atomic.AtomicReference<com.example.budgetapp.ui.overview.OverviewViewModel> home=new java.util.concurrent.atomic.AtomicReference<>();
   scenario.onActivity(a->home.set(a.overviewViewModel()));
   UiTestSupport.awaitValue(home.get().snapshot(),s->s.valid&&s.expenseFen==14700);
   capture("overview");
   scenario.onActivity(a->a.showLedger(a.overviewViewModel().filter(LedgerFilter.Metric.NET_OUTFLOW)));capture("ledger");
   java.util.concurrent.atomic.AtomicReference<com.example.budgetapp.ui.ledger.LedgerViewModel> ledger=new java.util.concurrent.atomic.AtomicReference<>();
   scenario.onActivity(a->{ledger.set(a.ledgerViewModel());ledger.get().setSearch("地铁");});
   UiTestSupport.awaitValue(ledger.get().rows(),rows->rows.size()==1&&rows.get(0).merchantRaw.equals("地铁出行"));
   scenario.onActivity(a->ledger.get().setSearch(""));
   var rows=UiTestSupport.awaitValue(ledger.get().rows(),items->items.size()==4);
   scenario.onActivity(a->a.showEditor(rows.get(0).id));capture("edit");
   scenario.onActivity(a->((androidx.fragment.app.DialogFragment)a.getSupportFragmentManager().findFragmentByTag("entry_editor_sheet")).dismissNow());
   scenario.onActivity(MainActivity::showStats);capture("stats");
   scenario.onActivity(a->a.showEditor(null));capture("entry");
   scenario.onActivity(a->{((androidx.fragment.app.DialogFragment)a.getSupportFragmentManager().findFragmentByTag("entry_editor_sheet")).dismissNow();assertTrue(a.getSupportFragmentManager().findFragmentById(R.id.ledger_container) instanceof com.example.budgetapp.ui.stats.StatsFragment);});
   scenario.onActivity(MainActivity::showReview);capture("review");
   scenario.onActivity(MainActivity::showSettings);capture("settings");
  }
 }
 private void capture(String name) throws Exception {
  android.os.SystemClock.sleep(800);
  var instrumentation=InstrumentationRegistry.getInstrumentation();instrumentation.waitForIdleSync();
  var image=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(image);
  File file=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"ui-"+name+".png");
  try(FileOutputStream stream=new FileOutputStream(file)){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}image.recycle();
  try(var output=instrumentation.getUiAutomation().executeShellCommand("cp "+file.getAbsolutePath()+" /sdcard/Download/budget-ui-"+name+".png")){try(var input=new FileInputStream(output.getFileDescriptor())){input.readAllBytes();}}
 }
}
