package com.example.budgetapp.ui;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
public final class UiTestSupport {
 private UiTestSupport(){}
 public static <T> T awaitValue(LiveData<T> source,Predicate<T> predicate) throws Exception {
  CountDownLatch latch=new CountDownLatch(1);AtomicReference<T> result=new AtomicReference<>();
  Observer<T> observer=v->{if(predicate.test(v)){result.set(v);latch.countDown();}};
  var instrumentation=InstrumentationRegistry.getInstrumentation();
  try {instrumentation.runOnMainSync(()->source.observeForever(observer));if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("Timed out waiting for state");return result.get();}
  finally {instrumentation.runOnMainSync(()->source.removeObserver(observer));}
 }
 public static void settle() throws Exception {
  com.example.budgetapp.BudgetApplication app=androidx.test.core.app.ApplicationProvider.getApplicationContext();
  app.container().io.submit(()->{}).get(10,TimeUnit.SECONDS);
  androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
 }
 public static void awaitDisplayed(int id) {
  long until=android.os.SystemClock.uptimeMillis()+10000;Throwable failure=null;
  while(android.os.SystemClock.uptimeMillis()<until){
   try{androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(id)).check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));return;}
   catch(androidx.test.espresso.NoMatchingViewException | AssertionError notReady){failure=notReady;android.os.SystemClock.sleep(80);}
  }
  throw new AssertionError("View was not displayed: "+id,failure);
 }
 public static void awaitView(androidx.test.core.app.ActivityScenario<? extends android.app.Activity> scenario,int id) throws Exception {
  CountDownLatch latch=new CountDownLatch(1);
  AtomicReference<android.view.View> root=new AtomicReference<>();
  AtomicReference<android.view.ViewTreeObserver.OnGlobalLayoutListener> registered=new AtomicReference<>();
  scenario.onActivity(activity->{android.view.View decor=activity.getWindow().getDecorView();root.set(decor);android.view.ViewTreeObserver.OnGlobalLayoutListener listener=()->{android.view.View found=decor.findViewById(id);if(found!=null&&found.isShown()&&!decor.isLayoutRequested())latch.countDown();};registered.set(listener);decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);listener.onGlobalLayout();});
  try {if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("View did not finish layout");}
  finally {androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{if(root.get()!=null&&root.get().getViewTreeObserver().isAlive())root.get().getViewTreeObserver().removeOnGlobalLayoutListener(registered.get());});}
 }
}
