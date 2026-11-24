package com.example.budgetapp.debug;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import com.example.budgetapp.R;
import com.example.budgetapp.capture.notification.NotificationSnapshot;
import com.example.budgetapp.capture.notification.ObservationSession;
import com.example.budgetapp.databinding.ActivityNotificationDiagnosticsBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class NotificationDiagnosticsActivity extends AppCompatActivity {
    private ActivityNotificationDiagnosticsBinding binding;
    private final ObservationSession session=ObservationSession.get();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean started;
    private boolean authorized;
    private NotificationSnapshot renderedLatest;
    private int renderedCount=-1;
    private final Runnable refresh=()->{ if (started) render(); };
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Raw diagnostics must not appear in recents previews or screen sharing.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        EdgeToEdge.enable(this);
        binding=ActivityNotificationDiagnosticsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowCompat.getInsetsController(getWindow(),binding.getRoot()).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(),binding.getRoot()).setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(),(v,insets)->{
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            return insets;
        });
        binding.backButton.setOnClickListener(v->finishObservation());
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finishObservation(); }
        });
        binding.startStop.setOnClickListener(v->toggle());
    }

    @Override protected void onStart() {
        super.onStart(); started=true; session.observe(refresh); render();
    }
    @Override protected void onResume() {
        super.onResume(); authorized=ObservationSession.isAuthorized(this);
        if (!authorized) session.closeWindow();
        render();
    }
    @Override protected void onStop() {
        started=false; handler.removeCallbacks(refresh); session.removeObserver(refresh);
        // Home keeps the short observation window alive, but no hidden UI copy
        // may outlive buffer expiry or a service disconnection.
        binding.samples.removeAllViews(); renderedLatest=null; renderedCount=-1;
        super.onStop();
    }
    @Override protected void onDestroy() {
        if (isFinishing()) session.closeWindow();
        binding.samples.removeAllViews(); renderedLatest=null;
        super.onDestroy();
    }
    private void finishObservation() { session.closeWindow(); finish(); }

    private void toggle() {
        if (session.isOpen()) { session.closeWindow(); return; }
        authorized=ObservationSession.isAuthorized(this);
        if (!authorized) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.permission_title)
                    .setMessage(R.string.permission_needed).setNegativeButton(R.string.cancel,null)
                    .setPositiveButton(R.string.open_system_settings,(dialog,which)->{
                        try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                        catch (ActivityNotFoundException absent) {
                            Toast.makeText(this,R.string.settings_unavailable,Toast.LENGTH_LONG).show();
                        }
                    }).show();
            return;
        }
        new MaterialAlertDialogBuilder(this).setTitle(R.string.diag_consent_title)
                .setMessage(R.string.diag_consent_body).setNegativeButton(R.string.cancel,null)
                .setPositiveButton(R.string.diag_consent_accept,(dialog,which)->{
                    // Recheck after the user decision instead of treating an old grant as current.
                    authorized=ObservationSession.isAuthorized(this);
                    if (authorized) session.openWindow();
                    else Toast.makeText(this,R.string.permission_needed,Toast.LENGTH_LONG).show();
                    render();
                }).show();
    }

    private void render() {
        boolean active=session.isOpen();
        binding.windowState.setText(active ? R.string.diag_active : R.string.diag_inactive);
        long seconds=(session.remainingMillis()+999)/1000;
        binding.countdown.setText(active ? getString(R.string.diag_remaining,seconds/60,seconds%60)
                : getString(R.string.diag_inactive_time));
        binding.startStop.setText(active ? R.string.diag_stop : R.string.diag_start);
        binding.connectionStatus.setText(!authorized ? R.string.service_description_off
                : !session.isConnected() ? R.string.service_description_waiting
                : active ? R.string.service_description_active : R.string.service_description_ready);
        binding.readError.setVisibility(session.hasReadError() ? View.VISIBLE : View.GONE);
        List<NotificationSnapshot> samples=session.latest();
        binding.sampleCount.setText(getString(R.string.sample_count,samples.size()));
        binding.emptyCard.setVisibility(samples.isEmpty() ? View.VISIBLE : View.GONE);
        NotificationSnapshot latest=samples.isEmpty() ? null : samples.get(samples.size()-1);
        if (renderedCount!=samples.size() || renderedLatest!=latest) {
            binding.samples.removeAllViews();
            for (int i=samples.size()-1;i>=Math.max(0,samples.size()-20);i--) addSample(samples.get(i));
            renderedCount=samples.size(); renderedLatest=latest;
        }
        handler.removeCallbacks(refresh);
        if (started && active) handler.postDelayed(refresh,1000);
    }

    private void addSample(NotificationSnapshot sample) {
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(16),dp(16),dp(16));
        card.setBackgroundResource(R.drawable.obs_card);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=dp(12); card.setLayoutParams(params);
        String platform=getString("com.tencent.mm".equals(sample.packageName)
                ? R.string.platform_wechat : R.string.platform_alipay);
        addText(card,getString(R.string.sample_meta,platform,
                sample.appVersion.isEmpty()?getString(R.string.unknown_version):sample.appVersion,
                TIME.format(Instant.ofEpochMilli(sample.capturedAtMs))),14,R.color.obs_green);
        addText(card,getString(R.string.raw_fields,display(sample.title),display(sample.text),
                display(sample.bigText),display(String.join("\n",sample.textLines))),13,R.color.obs_ink);
        addText(card,getString(R.string.sample_evidence,sample.packageName,display(sample.channelId),
                sample.notificationKey,TIME.format(Instant.ofEpochMilli(sample.postTimeMs)),
                TIME.format(Instant.ofEpochMilli(sample.capturedAtMs)),
                getString(sample.groupSummary?R.string.yes:R.string.no),
                getString(sample.truncated?R.string.yes:R.string.no)),11,R.color.obs_muted);
        binding.samples.addView(card);
    }
    private void addText(LinearLayout parent,String text,int sp,int color) {
        TextView view=new TextView(this); view.setText(text); view.setTextSize(sp);
        view.setTextColor(getColor(color)); view.setLineSpacing(dp(4),1);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        if (parent.getChildCount()>0) params.topMargin=dp(14);
        view.setLayoutParams(params); parent.addView(view);
    }
    private String display(String value) { return value.isEmpty()?getString(R.string.empty_field):value; }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
