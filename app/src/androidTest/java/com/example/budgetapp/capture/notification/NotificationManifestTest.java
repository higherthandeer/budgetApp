package com.example.budgetapp.capture.notification;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.XmlResourceParser;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.R;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

@RunWith(AndroidJUnit4.class)
public class NotificationManifestTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @Test public void listenerIsProtectedBySystemBindingPermission() throws Exception {
        ComponentName component = new ComponentName(context.getPackageName(),
                "com.example.budgetapp.capture.notification.PaymentNotificationService");
        ServiceInfo service = context.getPackageManager().getServiceInfo(component, 0);
        assertTrue(service.exported);
        assertEquals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", service.permission);
    }

    @Test public void observationRequestsNoUnrelatedSensitivePermissions() throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(),
                PackageManager.GET_PERMISSIONS | PackageManager.GET_SERVICES);
        Set<String> permissions = new HashSet<>(Arrays.asList(
                info.requestedPermissions == null ? new String[0] : info.requestedPermissions));
        for (String permission : new String[]{"android.permission.INTERNET",
                "android.permission.POST_NOTIFICATIONS", "android.permission.MANAGE_EXTERNAL_STORAGE",
                "android.permission.READ_SMS", "android.permission.READ_CONTACTS",
                "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"}) {
            assertFalse(permission, permissions.contains(permission));
        }
        if (info.services != null) for (ServiceInfo service : info.services) {
            assertNotEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", service.permission);
        }
        assertEquals(0, info.applicationInfo.flags & ApplicationInfo.FLAG_ALLOW_BACKUP);
    }

    @Test public void backupAndTransferRulesExcludeSensitiveDomains() throws Exception {
        assertExcluded(R.xml.backup_rules, "full-backup-content");
        assertExcluded(R.xml.data_extraction_rules, "cloud-backup");
        assertExcluded(R.xml.data_extraction_rules, "device-transfer");
        assertExcluded(R.xml.data_extraction_rules, "cross-platform-transfer");
    }

    @Test public void testingPackageDoesNotExposeDebugDiagnostics() throws Exception {
        assertTrue(context.getPackageName().endsWith(".testing"));
        try {
            context.getPackageManager().getActivityInfo(new ComponentName(context.getPackageName(),
                    "com.example.budgetapp.debug.NotificationDiagnosticsActivity"), 0);
            fail("Debug diagnostic Activity must not be packaged in testing");
        } catch (PackageManager.NameNotFoundException expected) {
            // An explicit debug source-set boundary, checked against the installed package.
        }
    }

    private void assertExcluded(int resource, String section) throws Exception {
        Set<String> domains = new HashSet<>();
        boolean inSection = false;
        try (XmlResourceParser xml = context.getResources().getXml(resource)) {
            for (int event = xml.getEventType(); event != XmlPullParser.END_DOCUMENT;
                    event = xml.next()) {
                if (event == XmlPullParser.START_TAG && section.equals(xml.getName())) inSection = true;
                else if (event == XmlPullParser.END_TAG && section.equals(xml.getName())) inSection = false;
                else if (event == XmlPullParser.START_TAG && inSection && "exclude".equals(xml.getName())
                        && ".".equals(xml.getAttributeValue(null, "path"))) {
                    domains.add(xml.getAttributeValue(null, "domain"));
                }
            }
        }
        assertTrue(section + ": " + domains,
                domains.containsAll(Arrays.asList("root", "file", "database", "sharedpref")));
    }
}
