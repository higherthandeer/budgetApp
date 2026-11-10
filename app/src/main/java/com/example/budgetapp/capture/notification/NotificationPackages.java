package com.example.budgetapp.capture.notification;

public final class NotificationPackages {
    private NotificationPackages() { }

    public static boolean isSupported(String packageName) {
        return "com.tencent.mm".equals(packageName)
                || "com.eg.android.AlipayGphone".equals(packageName);
    }
}
