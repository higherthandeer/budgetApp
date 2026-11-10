package com.example.budgetapp.capture.notification;

import static org.junit.Assert.*;
import org.junit.Test;

public class NotificationPackagesTest {
    @Test public void onlySupportedPaymentPackagesAreAccepted() {
        assertTrue(NotificationPackages.isSupported("com.tencent.mm"));
        assertTrue(NotificationPackages.isSupported("com.eg.android.AlipayGphone"));
        assertFalse(NotificationPackages.isSupported("com.example.chat"));
        assertFalse(NotificationPackages.isSupported("com.tencent.mm.evil"));
        assertFalse(NotificationPackages.isSupported("COM.TENCENT.MM"));
        assertFalse(NotificationPackages.isSupported(null));
        assertFalse(NotificationPackages.isSupported(""));
    }
}
