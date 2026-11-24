package com.example.budgetapp.capture.notification;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.Intent;
import android.widget.LinearLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.budgetapp.BuildConfig;
import com.example.budgetapp.MainActivity;
import java.lang.reflect.Field;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Synthetic lifecycle test; never grants notification access or reads payment apps. */
@RunWith(AndroidJUnit4.class)
public class DiagnosticsLifecycleTest {
    @Test public void stoppedPageDropsRawCopiesWithoutEndingObservation() throws Exception {
        assumeTrue("Run with -PdiagnosticsLifecycleTest", "debug".equals(BuildConfig.BUILD_TYPE));
        assertTrue(BuildConfig.APPLICATION_ID.endsWith(".testing"));
        Class<? extends Activity> page = Class.forName(
                "com.example.budgetapp.debug.NotificationDiagnosticsActivity").asSubclass(Activity.class);
        ObservationSession session = ObservationSession.get();
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), page)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity[] opened = new Activity[2];
        try {
            ActivityScenario<MainActivity> host = ActivityScenario.launch(MainActivity.class);
            host.onActivity(activity -> opened[0] = activity);
            ActivityScenario<Activity> scenario = ActivityScenario.launch(intent);
            scenario.onActivity(activity -> {
                opened[1] = activity;
                session.openWindow();
                session.capture(new NotificationSnapshot("com.tencent.mm", "synthetic", 1, 2,
                        "test", "Synthetic title", "Synthetic body", "", Collections.emptyList(),
                        false, false, "test"));
                // Keep seeding, rendering and lifecycle assertions in one main-thread
                // turn so pending observer callbacks cannot change the fixture.
                render(activity);
                assertEquals(1, samples(activity).getChildCount());
                // Exercise real callbacks without AndroidX's extra EmptyActivity, which
                // this ROM blocks when it tries to put another package in the foreground.
                InstrumentationRegistry.getInstrumentation().callActivityOnStop(activity);
                assertEquals("Stopped views must not retain raw text", 0, samples(activity).getChildCount());
                assertNull("Stopped page must not retain its last raw snapshot", latest(activity));
                assertTrue("Home must preserve the consent window", session.isOpen());
                InstrumentationRegistry.getInstrumentation().callActivityOnStart(activity);
                assertEquals("A live window repopulates on return", 1, samples(activity).getChildCount());
                InstrumentationRegistry.getInstrumentation().callActivityOnStop(activity);
                session.closeWindow();
                InstrumentationRegistry.getInstrumentation().callActivityOnStart(activity);
                assertEquals("An ended window cannot redisplay old text", 0, samples(activity).getChildCount());
            });
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                session.closeWindow();
                for (int i = opened.length - 1; i >= 0; i--)
                    if (opened[i] != null) opened[i].finish();
            });
        }
    }

    private static LinearLayout samples(Activity activity) {
        int id = activity.getResources().getIdentifier("samples", "id", activity.getPackageName());
        return activity.findViewById(id);
    }
    private static Object latest(Activity activity) {
        try {
            Field field = activity.getClass().getDeclaredField("renderedLatest");
            field.setAccessible(true);
            return field.get(activity);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
    private static void render(Activity activity) {
        try {
            java.lang.reflect.Method method = activity.getClass().getDeclaredMethod("render");
            method.setAccessible(true);
            method.invoke(activity);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
