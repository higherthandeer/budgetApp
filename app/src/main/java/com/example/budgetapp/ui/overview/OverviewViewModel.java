package com.example.budgetapp.ui.overview;

import android.app.Application;

import androidx.lifecycle.*;

import com.example.budgetapp.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.stats.StatsRepository;

import java.time.*;

public final class OverviewViewModel extends AndroidViewModel {
    public static final class Balance {
        public final long amountFen;
        public final boolean valid;
        Balance(long amountFen, boolean valid) { this.amountFen=amountFen; this.valid=valid; }
    }
    private final MediatorLiveData<Balance> balance = new MediatorLiveData<>();
    private LiveData<StatsRepository.Snapshot> balanceSource;
    private StatsRepository.Snapshot cumulative;
    private Long openingFen;
    public LiveData<Balance> balance() { return balance; }
    private void updateBalance() {
        if(openingFen==null||cumulative==null)return;
        try {
            balance.setValue(new Balance(cumulative.valid ? Math.addExact(openingFen,Math.subtractExact(cumulative.incomeFen,cumulative.expenseFen)) : 0,cumulative.valid));
        } catch(ArithmeticException overflow) { balance.setValue(new Balance(0,false)); }
    }
    private void refreshBalance() {
        if(balanceSource!=null)balance.removeSource(balanceSource);
        balanceSource=app.stats.observeTotals(()->new LedgerFilter(Long.MIN_VALUE,app.clock.millis(),LedgerFilter.Metric.NET_OUTFLOW,null,null));
        balance.addSource(balanceSource,value->{cumulative=value;updateBalance();});
    }

    public static final class ComparisonCard {
        public final String caption;
        public final com.example.budgetapp.stats.StatsRepository.PeriodComparison value;
        public final boolean valid;

        ComparisonCard(String caption, com.example.budgetapp.stats.StatsRepository.PeriodComparison value, boolean valid) {
            this.caption = caption;
            this.value = value;
            this.valid = valid;
        }
    }

    private final MutableLiveData<ComparisonCard> comparison = new MutableLiveData<>();
    private long comparisonGeneration;

    public LiveData<ComparisonCard> comparison() {
        return comparison;
    }

    private final SavedStateHandle state;
    public final AppContainer app;
    private final MediatorLiveData<StatsRepository.Snapshot> snapshot = new MediatorLiveData<>();
    private LiveData<StatsRepository.Snapshot> source;

    public OverviewViewModel(Application application, SavedStateHandle state) {
        super(application);
        this.state = state;
        app = ((BudgetApplication) application).container();
        balance.addSource(app.settings.observeOpeningBalanceFen(),value->{openingFen=value;updateBalance();});
        if (state.get("month") == null)
            state.set("month", YearMonth.now(app.clock.withZone(TimeWindows.LEDGER_ZONE)).toString());
        refresh();
    }

    public YearMonth month() {
        String value = state.get("month");
        return value == null ? YearMonth.now(app.clock.withZone(TimeWindows.LEDGER_ZONE)) : YearMonth.parse(value);
    }

    public void setMonth(YearMonth month) {
        if (month.isAfter(YearMonth.now(app.clock.withZone(TimeWindows.LEDGER_ZONE))))
            throw new IllegalArgumentException("Future month");
        state.set("month", month.toString());
        refresh();
    }

    public void setScope(String category, String provider) {
        state.set("category", category);
        state.set("provider", provider);
        refresh();
    }

    public LiveData<StatsRepository.Snapshot> snapshot() {
        return snapshot;
    }

    public LedgerFilter filter(LedgerFilter.Metric metric) {
        var s = snapshot.getValue();
        if (s == null) throw new IllegalStateException("No snapshot");
        return new LedgerFilter(s.filter.startMs, s.filter.endMs, metric, s.filter.categoryId, s.filter.provider);
    }

    public void refresh() {
        refreshBalance();
        YearMonth month = month();
        var range = TimeWindows.month(month);
        long end = month.equals(YearMonth.now(app.clock.withZone(TimeWindows.LEDGER_ZONE))) ? Math.min(range.endMs, app.clock.millis()) : range.endMs;
        LedgerFilter filter = new LedgerFilter(range.startMs, Math.max(range.startMs, end), LedgerFilter.Metric.NET_OUTFLOW, state.get("category"), state.get("provider"));
        if (source != null) snapshot.removeSource(source);
        source = app.stats.observe(() -> {
            long now = app.clock.millis();
            long liveEnd = month.equals(YearMonth.from(Instant.ofEpochMilli(now).atZone(TimeWindows.LEDGER_ZONE))) ? Math.min(range.endMs, now) : range.endMs;
            return new LedgerFilter(range.startMs, Math.max(range.startMs, liveEnd), filter.metric, filter.categoryId, filter.provider);
        });
        snapshot.addSource(source, value -> {
            snapshot.setValue(value);
            updateComparison(value);
        });
    }

    private void updateComparison(StatsRepository.Snapshot snapshot) {
        long generation = ++comparisonGeneration;
        if (!snapshot.valid) {
            comparison.setValue(new ComparisonCard(getApplication().getString(R.string.stats_error), null, false));
            return;
        }
        YearMonth selected = month();
        Clock captured = Clock.fixed(app.clock.instant(), TimeWindows.LEDGER_ZONE);
        boolean current = selected.equals(YearMonth.now(captured));
        java.util.Optional<com.example.budgetapp.stats.ComparisonWindows.Pair> optional = current ? com.example.budgetapp.stats.ComparisonWindows.monthToDate(captured) : java.util.Optional.of(com.example.budgetapp.stats.ComparisonWindows.completedMonth(selected));
        if (!optional.isPresent()) {
            comparison.setValue(new ComparisonCard(getApplication().getString(R.string.comparison_no_days), null, true));
            return;
        }
        var pair = optional.get();
        String caption = current ? getApplication().getString(R.string.comparison_days, pair.comparedDays, Instant.ofEpochMilli(pair.current.endMs - 1).atZone(TimeWindows.LEDGER_ZONE).toLocalDate().toString()) : getApplication().getString(R.string.comparison_months, selected.toString(), selected.minusMonths(1).toString());
        comparison.setValue(new ComparisonCard(caption, null, true));
        app.io.execute(() -> {
            try {
                var result = app.stats.periodComparison(snapshot.filter, pair);
                app.main.execute(() -> {
                    if (generation == comparisonGeneration)
                        comparison.setValue(new ComparisonCard(caption, result, true));
                });
            } catch (RuntimeException failure) {
                app.main.execute(() -> {
                    if (generation == comparisonGeneration)
                        comparison.setValue(new ComparisonCard(getApplication().getString(R.string.stats_error), null, false));
                });
            }
        });
    }

    @Override
    protected void onCleared() {
        comparisonGeneration++;
    }
}
