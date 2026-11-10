package com.example.budgetapp;

import android.os.Bundle;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.budgetapp.databinding.ActivityMainBinding;
import com.example.budgetapp.capture.notification.NotificationSnapshot;
import com.example.budgetapp.capture.notification.ObservationSession;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private boolean started;
    private final ObservationSession session = ObservationSession.get();
    private final Runnable observer = () -> { if (started) render(); };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        binding.permissionRow.setOnClickListener(v -> openSettings());
        binding.recheckRow.setOnClickListener(v -> render());
        if (hasDiagnostics()) {
            binding.diagnosticsHeading.setVisibility(View.VISIBLE);
            binding.diagnosticsCard.setVisibility(View.VISIBLE);
            binding.diagnosticsEntry.setOnClickListener(v -> startActivity(new Intent().setClassName(
                    getPackageName(),"com.example.budgetapp.debug.NotificationDiagnosticsActivity")));
        }
        render();
    }

    @Override protected void onStart() {
        super.onStart(); started = true; session.observe(observer); render();
    }
    @Override protected void onResume() { super.onResume(); render(); }
    @Override protected void onStop() {
        started = false; session.removeObserver(observer); super.onStop();
    }
    private boolean hasDiagnostics() {
        if (!BuildConfig.DEBUG) return false;
        try {
            getPackageManager().getActivityInfo(new ComponentName(getPackageName(),
                    "com.example.budgetapp.debug.NotificationDiagnosticsActivity"),0);
            return true;
        } catch (PackageManager.NameNotFoundException absent) { return false; }
    }
    private void openSettings() {
        try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
        catch (ActivityNotFoundException absent) {
            Toast.makeText(this,R.string.settings_unavailable,Toast.LENGTH_LONG).show();
        }
    }
    private void render() {
        boolean authorized = ObservationSession.isAuthorized(this);
        boolean connected = authorized && session.isConnected();
        if (!authorized && session.isOpen()) session.closeWindow();
        binding.permissionValue.setText(authorized ? R.string.permission_on : R.string.permission_off);
        binding.serviceState.setText(connected ? R.string.service_connected
                : authorized ? R.string.service_waiting : R.string.service_paused);
        binding.serviceState.setBackgroundTintList(ColorStateList.valueOf(getColor(
                connected ? R.color.obs_mint : R.color.obs_amber_bg)));
        binding.serviceState.setTextColor(getColor(connected ? R.color.obs_green : R.color.obs_amber));
        binding.serviceDescription.setText(!authorized ? R.string.service_description_off
                : !connected ? R.string.service_description_waiting
                : session.isOpen() ? R.string.service_description_active : R.string.service_description_ready);
        List<NotificationSnapshot> samples = session.latest();
        binding.sampleCount.setText(getString(R.string.sample_count,samples.size()));
        if (samples.isEmpty()) binding.sampleDescription.setText(R.string.no_session);
        else {
            NotificationSnapshot latest = samples.get(samples.size()-1);
            String time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"))
                    .format(Instant.ofEpochMilli(latest.capturedAtMs));
            binding.sampleDescription.setText(getString(R.string.last_sample,
                    getString("com.tencent.mm".equals(latest.packageName)
                            ? R.string.platform_wechat : R.string.platform_alipay),time));
        }
    }
}
