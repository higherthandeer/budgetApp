package com.example.budgetapp.ui;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class) public class PageRestoreUiTest {
 private void assertSurface(ActivityScenario<MainActivity> scenario,boolean observation,String fragment) {
  AtomicReference<int[]> visibility=new AtomicReference<>();AtomicReference<String> actual=new AtomicReference<>();
  scenario.onActivity(a->{visibility.set(new int[]{a.findViewById(R.id.observation_page).getVisibility(),a.findViewById(R.id.app_surface).getVisibility()});var current=a.getSupportFragmentManager().findFragmentById(R.id.ledger_container);actual.set(current==null?null:current.getClass().getSimpleName());});
  assertEquals("Observation surface visibility",observation?View.VISIBLE:View.GONE,visibility.get()[0]);
  assertEquals("App surface visibility",observation?View.GONE:View.VISIBLE,visibility.get()[1]);
  assertEquals("Restored fragment must keep the current page",fragment,actual.get());
 }
 @Test public void restoringHomeStatsAndSettingsKeepsOnlyOneSurface() throws Exception {
  for(String page:new String[]{"OverviewFragment","StatsFragment","SettingsFragment"}){
   try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
    scenario.onActivity(a->{if(page.equals("StatsFragment"))a.showStats();else if(page.equals("SettingsFragment"))a.showSettings();});
    UiTestSupport.settle();assertSurface(scenario,false,page);
    scenario.recreate();UiTestSupport.settle();assertSurface(scenario,false,page);
   }
  }
 }
 @Test public void observationRestoresExclusivelyAndBackReturnsToUnderlyingSettings() throws Exception {
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(MainActivity::showSettings);UiTestSupport.settle();
   scenario.onActivity(MainActivity::showObservation);assertSurface(scenario,true,"SettingsFragment");
   scenario.recreate();UiTestSupport.settle();assertSurface(scenario,true,"SettingsFragment");
   scenario.onActivity(a->a.getOnBackPressedDispatcher().onBackPressed());UiTestSupport.settle();assertSurface(scenario,false,"SettingsFragment");
   scenario.onActivity(a->a.getOnBackPressedDispatcher().onBackPressed());UiTestSupport.settle();assertSurface(scenario,false,"OverviewFragment");
  }
 }
}
