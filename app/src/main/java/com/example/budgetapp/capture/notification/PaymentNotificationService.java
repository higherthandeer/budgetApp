package com.example.budgetapp.capture.notification;

import android.app.Notification;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PaymentNotificationService extends NotificationListenerService {
    private static volatile PaymentNotificationService connectedInstance;
    private final Map<String, String> versions = new HashMap<>();
    private final ObservationSession session = ObservationSession.get();

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        cacheVersion("com.tencent.mm");
        cacheVersion("com.eg.android.AlipayGphone");
        session.connectionChanged(true);
        connectedInstance=this;
        app().health.onConnected();
        app().listenerRecovery.cancel();
        getSharedPreferences("health_metadata", MODE_PRIVATE).edit().putLong("connected", System.currentTimeMillis()).apply();
        app().recover();
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) for (StatusBarNotification notification : active)
                onNotificationPosted(notification);
        } catch (RuntimeException unavailable) {
            session.unreadableNotification();
        }
    }

    public static boolean isListenerConnected(){return connectedInstance!=null&&ObservationSession.get().isConnected();}

    private void releaseConnection(){
        if(connectedInstance!=null&&connectedInstance!=this)return;
        connectedInstance=null;session.connectionChanged(false);session.closeWindow();
        app().health.onDisconnected();app().ensureNotificationConnection(this);
    }
    @Override public void onListenerDisconnected(){releaseConnection();super.onListenerDisconnected();}
    @Override public void onDestroy(){releaseConnection();super.onDestroy();}
    @Override public void onTaskRemoved(android.content.Intent rootIntent){app().ensureNotificationConnection(this);super.onTaskRemoved(rootIntent);}

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !NotificationPackages.isSupported(sbn.getPackageName())) return;
        try {
            NotificationSnapshot snapshot=toSnapshot(sbn);
            if (session.isOpen()) session.capture(snapshot);
            app().health.onEvent();
            getSharedPreferences("health_metadata", MODE_PRIVATE).edit().putLong("event", snapshot.capturedAtMs).apply();
            app().intake.accept(snapshot);
        } catch (RuntimeException unreadable) {
            // Exception messages and payloads may contain personal information; do not log them.
            session.unreadableNotification();
        }
    }

    public static List<NotificationSnapshot> readCurrentNotifications() {
        PaymentNotificationService service=connectedInstance;
        if(service==null||!service.session.isConnected())throw new IllegalStateException("Listener not connected");
        service.cacheVersion("com.tencent.mm");service.cacheVersion("com.eg.android.AlipayGphone");
        StatusBarNotification[] notifications=service.getActiveNotifications();
        List<NotificationSnapshot> result=new ArrayList<>();
        if(notifications!=null)for(StatusBarNotification notification:notifications)
            if(notification!=null&&NotificationPackages.isSupported(notification.getPackageName()))result.add(service.toSnapshot(notification));
        return result;
    }

    private NotificationSnapshot toSnapshot(StatusBarNotification sbn) {
            Notification n = sbn.getNotification();
            Bundle extras = n.extras == null ? Bundle.EMPTY : n.extras;
            Content content = new Content();
            String title = content.read(extras.getCharSequence(Notification.EXTRA_TITLE));
            String text = content.read(extras.getCharSequence(Notification.EXTRA_TEXT));
            String bigText = content.read(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
            CharSequence[] sourceLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            List<String> lines = new ArrayList<>();
            if (sourceLines != null) {
                if (sourceLines.length > 32) content.truncated = true;
                for (int i = 0; i < Math.min(32, sourceLines.length); i++)
                    lines.add(content.read(sourceLines[i]));
            }
            return new NotificationSnapshot(sbn.getPackageName(), sbn.getKey(), sbn.getPostTime(),
                    System.currentTimeMillis(), n.getChannelId(), title, text, bigText, lines,
                    (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0, content.truncated,
                    versions.get(sbn.getPackageName()));
    }

    private com.example.budgetapp.AppContainer app() {
        return ((com.example.budgetapp.BudgetApplication) getApplication()).container();
    }

    private void cacheVersion(String packageName) {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(packageName, 0);
            versions.put(packageName, info.versionName);
        } catch (PackageManager.NameNotFoundException missing) {
            versions.put(packageName, "");
        }
    }

    private static final class Content {
        int remaining = 16384;
        boolean truncated;

        String read(CharSequence raw) {
            if (raw == null) return "";
            int end = Math.min(raw.length(), remaining);
            if (end > 0 && Character.isHighSurrogate(raw.charAt(end - 1))) end--;
            if (end < raw.length()) truncated = true;
            remaining -= end;
            return raw.subSequence(0, end).toString();
        }
    }
}
