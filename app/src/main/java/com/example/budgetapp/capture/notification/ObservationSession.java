package com.example.budgetapp.capture.notification;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.core.app.NotificationManagerCompat;
import com.example.budgetapp.BuildConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Process-local observation only; not a ledger or persistent health history. */
public final class ObservationSession {
    private static final ObservationSession INSTANCE = new ObservationSession();
    private final DiagnosticBuffer buffer = new DiagnosticBuffer(BuildConfig.DEBUG,new ElapsedClock());
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Runnable> observers = new CopyOnWriteArrayList<>();
    private final Runnable expiry = this::closeWindow;
    private volatile boolean connected;
    private volatile boolean readError;

    private ObservationSession() { }
    public static ObservationSession get() { return INSTANCE; }

    public static boolean isAuthorized(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            return manager != null && manager.isNotificationListenerAccessGranted(
                    new ComponentName(context,PaymentNotificationService.class));
        }
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.getPackageName());
    }

    public void openWindow() {
        buffer.openWindow();
        readError = false;
        main.removeCallbacks(expiry);
        if (buffer.isOpen()) main.postDelayed(expiry,15*60*1000L);
        changed();
    }

    public void closeWindow() {
        buffer.closeWindow();
        readError = false;
        main.removeCallbacks(expiry);
        changed();
    }

    public boolean isOpen() { return buffer.isOpen(); }
    public long remainingMillis() { return buffer.remainingMillis(); }
    public List<NotificationSnapshot> latest() { return buffer.latest(); }
    public boolean isConnected() { return connected; }
    public boolean hasReadError() { return readError; }
    void connectionChanged(boolean value) { connected=value; changed(); }
    void capture(NotificationSnapshot snapshot) {
        if (!buffer.isOpen()) return;
        buffer.accept(snapshot);
        changed();
    }
    void unreadableNotification() {
        if (buffer.isOpen()) { readError=true; changed(); }
    }
    public void observe(Runnable observer) { observers.addIfAbsent(observer); }
    public void removeObserver(Runnable observer) { observers.remove(observer); }
    private void changed() { for (Runnable observer:observers) main.post(observer); }

    /** Includes sleep and is unaffected by changes to the device wall clock. */
    private static final class ElapsedClock extends Clock {
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(SystemClock.elapsedRealtime()); }
    }
}
