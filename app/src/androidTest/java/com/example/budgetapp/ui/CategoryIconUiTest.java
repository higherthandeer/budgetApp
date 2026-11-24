package com.example.budgetapp.ui;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.*;
import com.example.budgetapp.data.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
@RunWith(AndroidJUnit4.class) public class CategoryIconUiTest {
 private BudgetApplication app;
 @Before public void setup() throws Exception {app=ApplicationProvider.getApplicationContext();assertTrue(app.getPackageName().endsWith(".testing"));app.container().io.submit(()->{app.container().db.clearAllTables();LedgerDatabase.seedDefaults(app.container().db.getOpenHelper().getWritableDatabase());}).get();}
 private int id(String name){int id=app.getResources().getIdentifier(name,"id",app.getPackageName());assertTrue("Missing icon picker: "+name,id!=0);return id;}
 @Test public void userChoosesIconsForBothFlowsAndChangesExistingCategory() throws Exception {
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   s.onActivity(MainActivity::showCategoryRules);UiTestSupport.awaitDisplayed(R.id.category_add);
   for(boolean income:new boolean[]{false,true}){
    onView(withId(R.id.category_add)).perform(click());onView(withId(R.id.category_name_input)).perform(replaceText(income?"自选收入":"自选支出"),closeSoftKeyboard());
    if(income)onView(withId(R.id.category_flow_income)).perform(click());
    onView(withId(id("category_icon_choose"))).perform(click());
    onView(withContentDescription("图标：咖啡")).perform(scrollTo(),click());
    if(!income){var instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();instrumentation.waitForIdleSync();var image=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(image);try(var out=new java.io.FileOutputStream(new java.io.File(app.getExternalFilesDir(null),"category-picker.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    onView(withText("使用此图标")).perform(click());
    onView(withId(android.R.id.button1)).perform(click());UiTestSupport.settle();UiTestSupport.awaitDisplayed(R.id.category_add);
   }
   var categories=app.container().io.submit(()->app.container().categories.categories()).get();
   for(var c:categories)if(c.name.startsWith("自选"))assertEquals("local_cafe",c.getClass().getField("iconKey").get(c));
   onView(withContentDescription("更换图标：自选支出")).perform(scrollTo(),click());
   onView(withContentDescription("图标：餐饮")).perform(scrollTo(),click());onView(withText("取消")).perform(click());UiTestSupport.settle();
   var unchanged=app.container().io.submit(()->app.container().categories.categories().stream().filter(c->c.name.equals("自选支出")).findFirst().get()).get();
   assertEquals("local_cafe",unchanged.getClass().getField("iconKey").get(unchanged));
   onView(withContentDescription("更换图标：自选支出")).perform(scrollTo(),click());onView(withContentDescription("图标：餐饮")).perform(scrollTo(),click());onView(withText("使用此图标")).perform(click());UiTestSupport.settle();
   var changed=app.container().io.submit(()->app.container().categories.categories().stream().filter(c->c.name.equals("自选支出")).findFirst().get()).get();
   assertEquals("restaurant",changed.getClass().getField("iconKey").get(changed));
  }
 }
 @Test public void expandedIconCanBeSearchedAndSaved() throws Exception {
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(MainActivity::showCategoryRules);UiTestSupport.awaitDisplayed(R.id.category_add);
   onView(withId(R.id.category_add)).perform(click());onView(withId(R.id.category_name_input)).perform(replaceText("水果费用"),closeSoftKeyboard());
   onView(withId(R.id.category_icon_choose)).perform(click());
   onView(withHint("搜索图标，如咖啡、交通")).perform(replaceText("水果"),closeSoftKeyboard());
   onView(withContentDescription("图标：水果")).perform(click());
   var instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();instrumentation.waitForIdleSync();var image=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(image);try(var out=new java.io.FileOutputStream(new java.io.File(app.getExternalFilesDir(null),"fruit-picker.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();
   onView(withText("使用此图标")).perform(click());
   onView(withId(android.R.id.button1)).perform(click());UiTestSupport.settle();
   var category=app.container().io.submit(()->app.container().categories.categories().stream().filter(c->c.name.equals("水果费用")).findFirst().get()).get();
   assertEquals("nutrition",category.iconKey);
  }
 }

}
