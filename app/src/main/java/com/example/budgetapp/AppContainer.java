package com.example.budgetapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.budgetapp.data.*;

import java.time.Clock;
import java.util.concurrent.*;

public final class AppContainer {
    public final LedgerDatabase db;
    public final LocalSettings settings;
    public final ManualLedgerRepository repository;
    public final CategoryRepository categories;
    public final ReviewRepository review;
    public final RawRetention retention;
    public final com.example.budgetapp.stats.StatsRepository stats;
    public final com.example.budgetapp.health.NotificationHealthController health;
    public final com.example.budgetapp.worker.RecoveryScheduler scheduler;
    public final com.example.budgetapp.capture.notification.EventIntake intake;
    public final com.example.budgetapp.parsing.NotificationParser parser;
    public final com.example.budgetapp.processing.TransactionProcessor processor;

    public final ExecutorService io = Executors.newSingleThreadExecutor();
    public final Executor main = command -> new Handler(Looper.getMainLooper()).post(command);
    public final androidx.lifecycle.MutableLiveData<com.example.budgetapp.capture.notification.CurrentNotificationReplay.Report> notificationReplay = new androidx.lifecycle.MutableLiveData<>(new com.example.budgetapp.capture.notification.CurrentNotificationReplay.Report(com.example.budgetapp.capture.notification.CurrentNotificationReplay.State.IDLE));
    private boolean replaying;
    public final androidx.lifecycle.MutableLiveData<com.example.budgetapp.health.NotificationHealth> notificationHealth = new androidx.lifecycle.MutableLiveData<>();
    public final com.example.budgetapp.worker.ListenerRecoveryScheduler listenerRecovery;
    public final Clock clock;

    public AppContainer(Context context, Clock clock) {
        this.clock = clock;
        db = LedgerDatabase.open(context);
        settings = new LocalSettings(context);
        repository = new ManualLedgerRepository(db, clock);
        categories = new CategoryRepository(db, clock);
        review = new ReviewRepository(db, clock);
        stats = new com.example.budgetapp.stats.StatsRepository(db, io);
        parser = new com.example.budgetapp.parsing.NotificationParser(com.example.budgetapp.parsing.TemplateCatalog.load(context));
        retention = new RawRetention(db, settings, clock);
        processor = new com.example.budgetapp.processing.TransactionProcessor(db, parser, new com.example.budgetapp.processing.CategoryMatcher(), clock, retention);
        var prefs = context.getSharedPreferences("health_metadata", Context.MODE_PRIVATE);
        health = new com.example.budgetapp.health.NotificationHealthController(clock, prefs.contains("connected") ? prefs.getLong("connected", 0) : null, android.os.SystemClock::elapsedRealtime);
        health.restoreHistory(prefs.contains("event") ? prefs.getLong("event", 0) : null, prefs.contains("parsed") ? prefs.getLong("parsed", 0) : null);
        health.addListener(() -> main.execute(() -> notificationHealth.setValue(health.snapshot())));
        listenerRecovery = new com.example.budgetapp.worker.ListenerRecoveryScheduler(context);
        scheduler = new com.example.budgetapp.worker.RecoveryScheduler(context);
        intake = new com.example.budgetapp.capture.notification.EventIntake(db, parser, settings, scheduler, io);
    }

    public void recover() {
        io.execute(() -> {
            retention.purgeExpired();
            if (!db.sourceEventDao().pending(1).isEmpty()) scheduler.schedule();
        });
    }

    public void requestRebind(Context context) {
        ensureNotificationConnection(context);
    }

    public void ensureNotificationConnection(Context context) {
        boolean authorized = com.example.budgetapp.capture.notification.ObservationSession.isAuthorized(context);
        health.onAuthorization(authorized);
        if (!authorized) {
            listenerRecovery.cancel();
            return;
        }
        if (com.example.budgetapp.capture.notification.PaymentNotificationService.isListenerConnected()) {
            listenerRecovery.cancel();
            return;
        }
        if (health.snapshot().connection == com.example.budgetapp.health.NotificationHealth.Connection.CONNECTED)
            health.onDisconnected();
        attemptNotificationRebind(context);
        listenerRecovery.schedule();
    }

    public void attemptNotificationRebind(Context context) {
        health.onAuthorization(com.example.budgetapp.capture.notification.ObservationSession.isAuthorized(context));
        if (health.shouldRequestRebind()) try {
            android.service.notification.NotificationListenerService.requestRebind(new android.content.ComponentName(context, com.example.budgetapp.capture.notification.PaymentNotificationService.class));
        } catch (RuntimeException unavailable) {
            health.onBacklog(health.snapshot().pendingCount, "LISTENER_REBIND_FAILED");
        }
    }

    public void replayCurrentNotifications(Context context) {
        if (replaying) return;
        if (!com.example.budgetapp.capture.notification.ObservationSession.isAuthorized(context)) {
            notificationReplay.setValue(new com.example.budgetapp.capture.notification.CurrentNotificationReplay.Report(com.example.budgetapp.capture.notification.CurrentNotificationReplay.State.NO_PERMISSION));
            return;
        }
        Context applicationContext = context.getApplicationContext();
        final java.util.List<com.example.budgetapp.capture.notification.NotificationSnapshot> snapshots;
        try {
            snapshots = com.example.budgetapp.capture.notification.PaymentNotificationService.readCurrentNotifications();
        } catch (SecurityException denied) {
            notificationReplay.setValue(new com.example.budgetapp.capture.notification.CurrentNotificationReplay.Report(com.example.budgetapp.capture.notification.CurrentNotificationReplay.State.NO_PERMISSION));
            return;
        } catch (IllegalStateException disconnected) {
            notificationReplay.setValue(new com.example.budgetapp.capture.notification.CurrentNotificationReplay.Report(com.example.budgetapp.capture.notification.CurrentNotificationReplay.State.NOT_CONNECTED));
            return;
        } catch (RuntimeException unavailable) {
            notificationReplay.setValue(new com.example.budgetapp.capture.notification.CurrentNotificationReplay.Report(com.example.budgetapp.capture.notification.CurrentNotificationReplay.State.FAILED));
            return;
        }
        replaying = true;
        notificationReplay.setValue(new com.example.budgetapp.capture.notification.CurrentNotificationReplay.Report(com.example.budgetapp.capture.notification.CurrentNotificationReplay.State.RUNNING));
        io.execute(() -> {
            com.example.budgetapp.capture.notification.CurrentNotificationReplay.Report result;
            try {
                result = new com.example.budgetapp.capture.notification.CurrentNotificationReplay(db, intake, processor, parser, retention).process(snapshots);
            } catch (RuntimeException failure) {
                result = new com.example.budgetapp.capture.notification.CurrentNotificationReplay.Report(com.example.budgetapp.capture.notification.CurrentNotificationReplay.State.FAILED);
            }
            var completed = result;
            main.execute(() -> {
                replaying = false;
                if (completed.state == com.example.budgetapp.capture.notification.CurrentNotificationReplay.State.COMPLETE) {
                    health.onParsed();
                    applicationContext.getSharedPreferences("health_metadata", Context.MODE_PRIVATE).edit().putLong("parsed", clock.millis()).apply();
                }
                notificationReplay.setValue(completed);
            });
        });
    }

}
