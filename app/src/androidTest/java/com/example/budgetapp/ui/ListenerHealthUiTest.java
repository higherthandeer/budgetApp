package com.example.budgetapp.ui;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.*;
import com.example.budgetapp.health.NotificationHealth;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
@RunWith(AndroidJUnit4.class) public class ListenerHealthUiTest {
 @Test public void callbacksUpdateConnectionWhileSettingsStayOpen() throws Exception {
  BudgetApplication app=ApplicationProvider.getApplicationContext();try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(MainActivity::showSettings);UiTestSupport.awaitView(scenario,R.id.settings_listener_connection);scenario.onActivity(a->app.container().health.onConnected());UiTestSupport.awaitValue(app.container().notificationHealth,h->h!=null&&h.connection==NotificationHealth.Connection.CONNECTED);onView(withId(R.id.settings_listener_connection)).check(matches(withText(app.getString(R.string.settings_connection,app.getString(R.string.service_connected)))));scenario.onActivity(a->app.container().health.onDisconnected());UiTestSupport.awaitValue(app.container().notificationHealth,h->h!=null&&h.connection==NotificationHealth.Connection.DISCONNECTED);onView(withId(R.id.settings_listener_connection)).check(matches(withText(app.getString(R.string.settings_connection,app.getString(R.string.settings_disconnected)))));}
 }
}
