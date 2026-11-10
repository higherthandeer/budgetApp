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
    private final Map<String,String> versions = new HashMap<>();
    private final ObservationSession session = ObservationSession.get();

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        cacheVersion("com.tencent.mm");
        cacheVersion("com.eg.android.AlipayGphone");
        session.connectionChanged(true);
    }
    @Override public void onListenerDisconnected() {
        session.connectionChanged(false);
        session.closeWindow();
        super.onListenerDisconnected();
    }
    @Override public void onDestroy() {
        session.connectionChanged(false);
        session.closeWindow();
        super.onDestroy();
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn==null || !NotificationPackages.isSupported(sbn.getPackageName()) || !session.isOpen()) return;
        try {
            Notification n=sbn.getNotification();
            Bundle extras=n.extras==null ? Bundle.EMPTY : n.extras;
            Content content=new Content();
            String title=content.read(extras.getCharSequence(Notification.EXTRA_TITLE));
            String text=content.read(extras.getCharSequence(Notification.EXTRA_TEXT));
            String bigText=content.read(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
            CharSequence[] sourceLines=extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            List<String> lines=new ArrayList<>();
            if (sourceLines!=null) {
                if (sourceLines.length>32) content.truncated=true;
                for (int i=0;i<Math.min(32,sourceLines.length);i++) lines.add(content.read(sourceLines[i]));
            }
            session.capture(new NotificationSnapshot(sbn.getPackageName(),sbn.getKey(),sbn.getPostTime(),
                    System.currentTimeMillis(),n.getChannelId(),title,text,bigText,lines,
                    (n.flags&Notification.FLAG_GROUP_SUMMARY)!=0,content.truncated,
                    versions.get(sbn.getPackageName())));
        } catch (RuntimeException unreadable) {
            // Exception messages and payloads may contain personal information; do not log them.
            session.unreadableNotification();
        }
    }
    private void cacheVersion(String packageName) {
        try {
            PackageInfo info=getPackageManager().getPackageInfo(packageName,0);
            versions.put(packageName,info.versionName);
        } catch (PackageManager.NameNotFoundException missing) { versions.put(packageName,""); }
    }
    private static final class Content {
        int remaining=16384;
        boolean truncated;
        String read(CharSequence raw) {
            if (raw==null) return "";
            int end=Math.min(raw.length(),remaining);
            if (end>0 && Character.isHighSurrogate(raw.charAt(end-1))) end--;
            if (end<raw.length()) truncated=true;
            remaining-=end;
            return raw.subSequence(0,end).toString();
        }
    }
}
