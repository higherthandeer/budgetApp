package com.example.budgetapp.ui;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.*;
import com.example.budgetapp.capture.notification.CurrentNotificationReplay;
import org.junit.*;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class SettingsNotificationReplayTest {
 @Test public void buttonShowsPermissionOrConnectionProblemWithoutBooking() throws Exception {BudgetApplication app=ApplicationProvider.getApplicationContext();try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(MainActivity::showSettings);UiTestSupport.awaitView(scenario,R.id.settings_advanced);UiTestSupport.settle();onView(withId(R.id.settings_advanced)).perform(scrollTo(),click());UiTestSupport.awaitDisplayed(R.id.notification_replay_button);onView(withId(R.id.notification_replay_button)).perform(scrollTo(),click());var report=UiTestSupport.awaitValue(app.container().notificationReplay,r->r!=null&&(r.state==CurrentNotificationReplay.State.NO_PERMISSION||r.state==CurrentNotificationReplay.State.NOT_CONNECTED));assertEquals(0,report.posted);onView(withId(R.id.notification_replay_button)).check(matches(isEnabled()));}}
 @Test public void runningAndResultSurvivePageRefresh() throws Exception {BudgetApplication app=ApplicationProvider.getApplicationContext();try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(a->{a.showSettings();app.container().notificationReplay.setValue(new CurrentNotificationReplay.Report(CurrentNotificationReplay.State.RUNNING));});UiTestSupport.awaitView(scenario,R.id.settings_advanced);UiTestSupport.settle();onView(withId(R.id.settings_advanced)).perform(scrollTo(),click());UiTestSupport.awaitDisplayed(R.id.notification_replay_button);onView(withId(R.id.notification_replay_button)).check(matches(not(isEnabled())));scenario.onActivity(a->{var report=new CurrentNotificationReplay.Report(CurrentNotificationReplay.State.COMPLETE);report.seen=2;report.posted=1;report.unchanged=1;app.container().notificationReplay.setValue(report);});onView(withId(R.id.notification_replay_result)).check(matches(withText(app.getString(R.string.notification_replay_summary,2,1,0,0,0,1,0))));scenario.recreate();UiTestSupport.awaitView(scenario,R.id.settings_advanced);UiTestSupport.settle();onView(withId(R.id.settings_advanced)).perform(scrollTo(),click());UiTestSupport.awaitDisplayed(R.id.notification_replay_button);onView(withId(R.id.notification_replay_button)).check(matches(isEnabled()));}}
}
