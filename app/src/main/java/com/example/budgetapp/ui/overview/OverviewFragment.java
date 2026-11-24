package com.example.budgetapp.ui.overview;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;

import com.example.budgetapp.*;
import com.example.budgetapp.processing.*;

import java.time.*;

public final class OverviewFragment extends Fragment {
    private OverviewViewModel vm;

    public OverviewFragment() {
        super(R.layout.fragment_overview);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MainActivity activity = (MainActivity) requireActivity();
        vm = activity.overviewViewModel();view.findViewById(R.id.overview_review).setBackgroundTintList(null);
        view.findViewById(R.id.overview_month).setOnClickListener(v -> com.example.budgetapp.ui.common.MonthPicker.show(requireContext(),vm.month(),YearMonth.now(vm.app.clock.withZone(TimeWindows.LEDGER_ZONE)),vm::setMonth));
        activity.ledgerViewModel().reviews().observe(getViewLifecycleOwner(),events -> ((Button)view.findViewById(R.id.overview_review)).setText(events.isEmpty()?"都核对好了 · 暂无待核对记录":"有 "+events.size()+" 笔记录待核对  ›"));
        view.findViewById(R.id.ledger_add).setOnClickListener(v -> activity.showEditor(null));
        view.findViewById(R.id.ledger_settings).setOnClickListener(v -> activity.showSettings());
        view.findViewById(R.id.overview_bills).setOnClickListener(v -> activity.showLedger(vm.filter(LedgerFilter.Metric.NET_OUTFLOW)));
        view.findViewById(R.id.overview_review).setOnClickListener(v -> activity.showReview());
        view.findViewById(R.id.overview_previous).setOnClickListener(v -> vm.setMonth(vm.month().minusMonths(1)));
        view.findViewById(R.id.overview_next).setOnClickListener(v -> vm.setMonth(vm.month().plusMonths(1)));
        int[] ids = {R.id.overview_expense, R.id.overview_income};
        LedgerFilter.Metric[] metrics = {LedgerFilter.Metric.EXPENSE, LedgerFilter.Metric.INCOME};
        for (int i = 0; i < ids.length; i++) {
            LedgerFilter.Metric metric = metrics[i];
            view.findViewById(ids[i]).setOnClickListener(v -> activity.showLedger(vm.filter(metric)));
        }
        view.findViewById(R.id.overview_expense_card).setEnabled(false);
        view.findViewById(R.id.overview_expense_card).setOnClickListener(v->activity.showLedger(vm.filter(LedgerFilter.Metric.EXPENSE)));
        vm.balance().observe(getViewLifecycleOwner(),balance->((TextView)view.findViewById(R.id.overview_balance)).setText(balance.valid?"¥ "+Money.formatFen(balance.amountFen):getString(R.string.stats_unavailable)));
        view.findViewById(R.id.overview_bills).setEnabled(false);for(int id:ids)view.findViewById(id).setEnabled(false);
        vm.snapshot().observe(getViewLifecycleOwner(), snapshot -> {
            view.findViewById(R.id.overview_bills).setEnabled(snapshot.valid);view.findViewById(R.id.overview_expense_card).setEnabled(snapshot.valid);
            ((TextView) view.findViewById(R.id.overview_month)).setText(vm.month().getYear()+"年"+vm.month().getMonthValue()+"月 ▾");
            view.findViewById(R.id.overview_next).setEnabled(vm.month().isBefore(YearMonth.now(vm.app.clock.withZone(TimeWindows.LEDGER_ZONE))));
            ((TextView) view.findViewById(R.id.overview_error)).setText(snapshot.valid ? "" : getString(R.string.stats_error));
            long surplus = snapshot.valid ? Math.subtractExact(snapshot.incomeFen,snapshot.expenseFen) : 0;
            TextView surplusText=view.findViewById(R.id.overview_net);
            surplusText.setText(snapshot.valid?"¥ "+Money.formatFen(surplus):getString(R.string.stats_unavailable));
            surplusText.setTextColor(requireContext().getColor(surplus<0?R.color.obs_red:R.color.obs_green));
            boolean currentMonth=vm.month().equals(YearMonth.now(vm.app.clock.withZone(TimeWindows.LEDGER_ZONE)));
            ((TextView)view.findViewById(R.id.overview_expense_label)).setText(currentMonth?"本月支出":"当月支出");
            ((TextView)view.findViewById(R.id.overview_surplus_label)).setText(currentMonth?"本月结余":"当月结余");
            ((TextView)view.findViewById(R.id.overview_income_label)).setText(currentMonth?"本月收入":"当月收入");
            long[] values = {snapshot.expenseFen, snapshot.incomeFen, snapshot.netOutflowFen};
            for (int i = 0; i < ids.length; i++) {
                ((TextView) view.findViewById(ids[i])).setText(snapshot.valid ? "¥ "+Money.formatFen(values[i]) : getString(R.string.stats_unavailable));
                view.findViewById(ids[i]).setEnabled(snapshot.valid);
            }
            view.findViewById(R.id.overview_empty).setVisibility(snapshot.valid && snapshot.expenseFen == 0 ? View.VISIBLE : View.GONE);
            LinearLayout categories = view.findViewById(R.id.overview_categories);
            categories.removeAllViews();
            if (snapshot.valid) {
                java.util.Map<String,String> categoryNames=new java.util.HashMap<>(),categoryIcons=new java.util.HashMap<>();for(var entry:snapshot.entries){categoryNames.put(entry.transaction.categoryId,entry.categoryName);categoryIcons.put(entry.transaction.categoryId,entry.categoryIconKey);}
                java.util.List<com.example.budgetapp.data.entity.LedgerTransactionEntity> recent=new java.util.ArrayList<>();
                for(var entry:snapshot.entries)recent.add(entry.transaction);
                view.findViewById(R.id.overview_empty).setVisibility(recent.isEmpty()?View.VISIBLE:View.GONE);
                for(var tx:recent.subList(0,Math.min(3,recent.size()))){
                    View item=getLayoutInflater().inflate(R.layout.item_ledger,categories,false);
                    com.example.budgetapp.ui.ledger.LedgerAdapter.bindTransaction(item,tx,false,categoryNames.get(tx.categoryId),categoryIcons.get(tx.categoryId));
                    item.setOnClickListener(v->activity.showEditor(tx.id));categories.addView(item);
                }
            }
        });
        vm.comparison().observe(getViewLifecycleOwner(), card -> {
            TextView text = view.findViewById(R.id.overview_comparison);
            String value = card.caption;
            if (card.value != null) {
                var result = card.value.spending;
                value += "\n" + result.label + (result.percent == null ? "" : getString(R.string.comparison_percent, result.percent.toPlainString())) + "\n" + result.explanation + "\n" + getString(R.string.comparison_other_values, Money.formatFen(card.value.incomeDeltaFen), Money.formatFen(card.value.netDeltaFen));
            }
            text.setText(value);
        });
        vm.refresh();
    }
}
