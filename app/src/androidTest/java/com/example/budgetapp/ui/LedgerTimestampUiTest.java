package com.example.budgetapp.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.*;
import com.example.budgetapp.data.LedgerDatabase;
import com.example.budgetapp.data.EventCodec;
import com.example.budgetapp.data.entity.SourceEventEntity;
import com.example.budgetapp.parsing.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.ui.ledger.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LedgerTimestampUiTest {
    private BudgetApplication app;

    @Before public void setUp() throws Exception {
        app = ApplicationProvider.getApplicationContext();
        assertTrue(app.getPackageName().endsWith(".testing"));
        app.container().io.submit(() -> {
            app.container().db.clearAllTables();
            LedgerDatabase.seedDefaults(app.container().db.getOpenHelper().getWritableDatabase());
        }).get(10, TimeUnit.SECONDS);
    }

    @Test public void manualEntryUsesActualSaveTimeAfterQueuedWorkAndKeepsItOnEdit() throws Exception {
        CountDownLatch queued = new CountDownLatch(1), release = new CountDownLatch(1);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<LedgerViewModel> vm = new AtomicReference<>();
            UiTestSupport.settle();
            app.container().io.execute(() -> {
                queued.countDown();
                try { release.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            assertTrue(queued.await(10, TimeUnit.SECONDS));
            scenario.onActivity(activity -> {
                vm.set(activity.ledgerViewModel());
                vm.get().beginEditor(null);
                vm.get().field("amount", "12.34", true);
                vm.get().field("merchant", "保存时间测试", true);
                vm.get().field("date", "2020-01-01", true);
                vm.get().save(false);
            });
            // Cross at least one clock tick after save was queued, before persistence starts.
            long releasedAt = app.container().clock.millis();
            while (app.container().clock.millis() == releasedAt) Thread.yield();
            releasedAt = app.container().clock.millis();
            release.countDown();
            var rows = UiTestSupport.awaitValue(vm.get().rows(), value -> value != null && value.size() == 1);
            var saved = rows.get(0);
            assertTrue("Timestamp must be taken on the IO save path", saved.occurredAtMs >= releasedAt);
            assertTrue(saved.occurredAtMs <= app.container().clock.millis());
            assertEquals("SECOND", saved.timePrecision);
            scenario.onActivity(activity -> vm.get().beginEditor(saved.id));
            UiTestSupport.awaitValue(vm.get().editing, value -> value != null && value.id.equals(saved.id));
            scenario.onActivity(activity -> { vm.get().field("note", "只改备注", true); vm.get().save(false); });
            var edited = UiTestSupport.awaitValue(vm.get().rows(), value -> value != null && value.size() == 1 && value.get(0).revision == 2).get(0);
            assertEquals(saved.occurredAtMs, edited.occurredAtMs);
            assertEquals("SECOND", edited.timePrecision);
        } finally {
            release.countDown();
            app.container().io.submit(() -> {}).get(10, TimeUnit.SECONDS);
        }
    }

    @Test public void ledgerRowsAndDetailsDisplaySeconds() throws Exception {
        long at = Instant.parse("2026-11-21T02:03:47Z").toEpochMilli();
        String id = app.container().io.submit(() -> app.container().repository.save("display-seconds",
                new TransactionDraft(Kind.EXPENSE, 1234, at, TimePrecision.SECOND, "秒钟显示测试", "OTHER", "scope", null, ""), true).transactionId).get();
        var tx = app.container().io.submit(() -> app.container().db.ledgerDao().find(id)).get();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<String> rowTime = new AtomicReference<>();
            scenario.onActivity(activity -> {
                View row = LayoutInflater.from(activity).inflate(R.layout.item_ledger, null, false);
                LedgerAdapter.bindTransaction(row, tx, false);
                rowTime.set(((TextView) row.findViewById(R.id.row_date)).getText().toString());
                LedgerDetailBottomSheet.show(activity, id);
            });
            assertTrue(rowTime.get().contains("10:03:47"));
            UiTestSupport.awaitDisplayed(R.id.detail_edit);
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(
                    org.hamcrest.Matchers.containsString("10:03:47"))).check(androidx.test.espresso.assertion.ViewAssertions.matches(
                    androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
        }
    }

    @Test public void confirmingPendingNotificationPreservesItsPrecisePostTime() throws Exception {
        long posted = Instant.parse("2026-11-21T02:03:47.321Z").toEpochMilli();
        app.container().io.submit(() -> {
            ParsedTransaction candidate = new ParsedTransaction();
            candidate.kind = Kind.EXPENSE;
            candidate.amountMinor = 1234L;
            candidate.currency = "CNY";
            candidate.occurredAtMs = posted;
            candidate.timePrecision = TimePrecision.SECOND;
            candidate.dateBasis = ParsedTransaction.DateBasis.NOTIFICATION_POST_TIME;
            candidate.merchant = "通知确认测试";
            SourceEventEntity event = new SourceEventEntity();
            event.id = "precise-review";
            event.eventKey = event.id;
            event.sourceType = "NOTIFICATION";
            event.provider = "WECHAT";
            event.accountScopeId = "scope";
            event.processState = "REVIEW";
            event.candidateJson = EventCodec.encodeParsed(new ParseResult(ParseResult.Disposition.REVIEW,
                    candidate, "POSSIBLE_DUPLICATE", "test", 1));
            app.container().db.sourceEventDao().insertIgnore(event);
        }).get();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<com.example.budgetapp.ui.review.ReviewViewModel> vm = new AtomicReference<>();
            scenario.onActivity(activity -> { activity.showReview(); vm.set(activity.reviewViewModel()); });
            UiTestSupport.awaitValue(vm.get().rows(), rows -> rows != null && rows.size() == 1);
            UiTestSupport.awaitView(scenario, R.id.review_new);
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.review_new))
                    .perform(androidx.test.espresso.action.ViewActions.click());
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.category_apply))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(androidx.test.espresso.action.ViewActions.click());
            UiTestSupport.awaitValue(vm.get().rows(), rows -> rows != null && rows.isEmpty());
            var tx = app.container().io.submit(() -> app.container().db.ledgerDao().find(
                    app.container().db.sourceEventDao().find("precise-review").transactionId)).get();
            assertEquals(posted, tx.occurredAtMs);
            assertEquals("SECOND", tx.timePrecision);
        }
    }
}
