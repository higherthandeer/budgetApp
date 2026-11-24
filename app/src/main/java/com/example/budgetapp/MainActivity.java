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
import androidx.core.view.WindowCompat;

import com.example.budgetapp.databinding.ActivityMainBinding;
import com.example.budgetapp.capture.notification.NotificationSnapshot;
import com.example.budgetapp.capture.notification.ObservationSession;
import com.example.budgetapp.ui.ledger.EntryEditorFragment;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String STATE_OBSERVATION_VISIBLE = "budget.observation_visible";
    private ActivityMainBinding binding;
    private boolean started;
    private boolean changingNavigation;
    private final androidx.activity.OnBackPressedCallback observationBack = new androidx.activity.OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            binding.observationPage.setVisibility(View.GONE);
            binding.ledgerContainer.setVisibility(View.VISIBLE);
            binding.appSurface.setVisibility(View.VISIBLE);
            setEnabled(false);
        }
    };
    private final ObservationSession session = ObservationSession.get();
    private final Runnable observer = () -> {
        if (started) render();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getOnBackPressedDispatcher().addCallback(this, observationBack);

        // 绑定导航条
        binding.bottomNavigation.inflateMenu(R.menu.bottom_navigation);

        // 始终显示文字、关闭选中时的水平位移、选中为绿色、未选中为灰色、选中指示背景为薄荷色。
        binding.bottomNavigation.setLabelVisibilityMode(com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED);
        binding.bottomNavigation.setItemHorizontalTranslationEnabled(false);
        ColorStateList navigationColors = new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{getColor(R.color.obs_green), getColor(R.color.obs_muted)});
        binding.bottomNavigation.setItemIconTintList(navigationColors);
        binding.bottomNavigation.setItemTextColor(navigationColors);
        binding.bottomNavigation.setItemActiveIndicatorColor(ColorStateList.valueOf(getColor(R.color.obs_mint)));
        binding.globalAdd.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.obs_green)));
        binding.globalAdd.setImageTintList(ColorStateList.valueOf(getColor(R.color.obs_white)));

        // 设置导航绑定Fragment
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            if (changingNavigation) return true;
            int id = item.getItemId();
            if (id == R.id.nav_overview) showOverview();
            else if (id == R.id.nav_ledger)
                root(new com.example.budgetapp.ui.ledger.LedgerFragment(), R.id.nav_ledger);
            else if (id == R.id.nav_stats) showStats();
            else if (id == R.id.nav_review)
                root(new com.example.budgetapp.ui.review.ReviewFragment(), R.id.nav_review);
            else if (id == R.id.nav_add) {
                showEditor(null);

                // 这是一个操作按钮，点击后保留原来的导航选中项。
                return false;
            }
            return true;
        });
        binding.globalAdd.setOnClickListener(v -> showEditor(null));
        ledgerViewModel().reviews().observe(this, events -> {
            if (events.isEmpty()) binding.bottomNavigation.removeBadge(R.id.nav_review);
            else {
                var badge = binding.bottomNavigation.getOrCreateBadge(R.id.nav_review);
                badge.setNumber(events.size());
                badge.setBackgroundColor(getColor(R.color.obs_amber));
                badge.setBadgeTextColor(getColor(R.color.obs_white));
            }
        });
        getSupportFragmentManager().addOnBackStackChangedListener(this::syncNavigation);
        binding.bottomNavigation.post(this::syncNavigation);
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightNavigationBars(true);
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
                    getPackageName(), "com.example.budgetapp.debug.NotificationDiagnosticsActivity")));
        }
        render();
        if (savedInstanceState == null) showOverview();
        else if (savedInstanceState.getBoolean(STATE_OBSERVATION_VISIBLE, false)) showObservation();
        else surface();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_OBSERVATION_VISIBLE,
                binding.observationPage.getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(outState);
    }

    public com.example.budgetapp.ui.ledger.LedgerViewModel ledgerViewModel() {
        return new androidx.lifecycle.ViewModelProvider(this).get(com.example.budgetapp.ui.ledger.LedgerViewModel.class);
    }

    public com.example.budgetapp.ui.overview.OverviewViewModel overviewViewModel() {
        return new androidx.lifecycle.ViewModelProvider(this).get(com.example.budgetapp.ui.overview.OverviewViewModel.class);
    }

    public com.example.budgetapp.ui.stats.StatsViewModel statsViewModel() {
        return new androidx.lifecycle.ViewModelProvider(this).get(com.example.budgetapp.ui.stats.StatsViewModel.class);
    }

    public com.example.budgetapp.ui.review.ReviewViewModel reviewViewModel() {
        return new androidx.lifecycle.ViewModelProvider(this).get(com.example.budgetapp.ui.review.ReviewViewModel.class);
    }

    private void surface() {
        binding.observationPage.setVisibility(View.GONE);
        binding.appSurface.setVisibility(View.VISIBLE);
        binding.ledgerContainer.setVisibility(View.VISIBLE);
        observationBack.setEnabled(false);
    }

    private void selectNavigation(int id) {
        changingNavigation = true;
        binding.bottomNavigation.setSelectedItemId(id);
        changingNavigation = false;
    }

    private void syncNavigation() {
        androidx.fragment.app.Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.ledger_container);
        if (fragment instanceof com.example.budgetapp.ui.overview.OverviewFragment)
            selectNavigation(R.id.nav_overview);
        else if (fragment instanceof com.example.budgetapp.ui.ledger.LedgerFragment)
            selectNavigation(R.id.nav_ledger);
        else if (fragment instanceof com.example.budgetapp.ui.stats.StatsFragment)
            selectNavigation(R.id.nav_stats);
        else if (fragment instanceof com.example.budgetapp.ui.review.ReviewFragment)
            selectNavigation(R.id.nav_review);
        binding.globalAdd.setVisibility(View.GONE);
    }

    private void root(androidx.fragment.app.Fragment fragment, int item) {
        surface();
        getSupportFragmentManager().popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        selectNavigation(item);
        binding.globalAdd.setVisibility(View.GONE);
        getSupportFragmentManager().beginTransaction().replace(R.id.ledger_container, fragment).commit();
    }

    private void leaf(androidx.fragment.app.Fragment fragment) {
        surface();
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction().replace(R.id.ledger_container, fragment);
        if (getSupportFragmentManager().findFragmentById(R.id.ledger_container) != null)
            transaction.addToBackStack(null);
        transaction.commit();
    }

    public void showOverview() {
        root(new com.example.budgetapp.ui.overview.OverviewFragment(), R.id.nav_overview);
    }

    public void showStats() {
        root(new com.example.budgetapp.ui.stats.StatsFragment(), R.id.nav_stats);
    }

    public void showLedger(com.example.budgetapp.processing.LedgerFilter filter) {
        ledgerViewModel().setFilter(filter);
        selectNavigation(R.id.nav_ledger);
        leaf(new com.example.budgetapp.ui.ledger.LedgerFragment());
    }

    public void showEditor(String txId) {
        androidx.fragment.app.FragmentManager manager =
                getSupportFragmentManager();

        // 页面状态已经保存时，不再发起弹窗操作。
        if (manager.isStateSaved()) {
            return;
        }

        // 防止连续点击打开两个记账弹层。
        if (manager.findFragmentByTag(EntryEditorFragment.TAG) != null) {
            return;
        }

        // null 表示新增；有账单 ID 表示编辑。
        ledgerViewModel().beginEditor(txId);

        EntryEditorFragment sheet = new EntryEditorFragment();
        sheet.showNow(manager, EntryEditorFragment.TAG);
    }

    public void showReview() {
        selectNavigation(R.id.nav_review);
        leaf(new com.example.budgetapp.ui.review.ReviewFragment());
    }

    public void showCategoryRules() {
        leaf(new com.example.budgetapp.ui.category.CategoryRulesFragment());
    }

    public void showSettings() {
        leaf(new com.example.budgetapp.ui.settings.SettingsFragment());
    }

    public void showObservation() {
        observationBack.setEnabled(true);
        binding.appSurface.setVisibility(View.GONE);
        binding.observationPage.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ((BudgetApplication) getApplication()).container().recover();
        ((BudgetApplication) getApplication()).container().ensureNotificationConnection(this);
        started = true;
        session.observe(observer);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onStop() {
        started = false;
        session.removeObserver(observer);
        super.onStop();
    }

    private boolean hasDiagnostics() {
        if (!BuildConfig.DEBUG) return false;
        try {
            getPackageManager().getActivityInfo(new ComponentName(getPackageName(),
                    "com.example.budgetapp.debug.NotificationDiagnosticsActivity"), 0);
            return true;
        } catch (PackageManager.NameNotFoundException absent) {
            return false;
        }
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (ActivityNotFoundException absent) {
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show();
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
        binding.sampleCount.setText(getString(R.string.sample_count, samples.size()));
        if (samples.isEmpty()) binding.sampleDescription.setText(R.string.no_session);
        else {
            NotificationSnapshot latest = samples.get(samples.size() - 1);
            String time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"))
                    .format(Instant.ofEpochMilli(latest.capturedAtMs));
            binding.sampleDescription.setText(getString(R.string.last_sample,
                    getString("com.tencent.mm".equals(latest.packageName)
                            ? R.string.platform_wechat : R.string.platform_alipay), time));
        }
    }
}
