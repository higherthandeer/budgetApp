package com.example.budgetapp.ui.ledger;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.text.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.example.budgetapp.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.stats.StatsRepository;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class LedgerFragment extends Fragment {
 private LedgerViewModel vm;
 private LedgerAdapter adapter;
 private String categoryLabel="分类";
 public LedgerFragment(){super(R.layout.fragment_ledger);}
 @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state){
  MainActivity activity=(MainActivity)requireActivity();vm=activity.ledgerViewModel();
  RecyclerView list=view.findViewById(R.id.ledger_list);list.setLayoutManager(new LinearLayoutManager(requireContext()));adapter=new LedgerAdapter(activity::showEditor);list.setAdapter(adapter);
  EditText search=view.findViewById(R.id.ledger_search);search.setText(vm.search());search.setVisibility(vm.search().isEmpty()?View.GONE:View.VISIBLE);
  view.findViewById(R.id.ledger_settings).setOnClickListener(v->{search.setVisibility(search.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);if(search.getVisibility()==View.VISIBLE)search.requestFocus();});
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence t,int a,int c,int f){}public void onTextChanged(CharSequence t,int a,int b,int c){}public void afterTextChanged(Editable t){vm.setSearch(t.toString());}});
  view.findViewById(R.id.ledger_clear).setOnClickListener(v->{search.setText("");var range=TimeWindows.month(selectedMonth());vm.setFilter(new LedgerFilter(range.startMs,range.endMs,vm.filter().metric,null,null));});
  view.findViewById(R.id.ledger_category).setOnClickListener(v->{var app=((BudgetApplication)activity.getApplication()).container();app.io.execute(()->{var categories=app.categories.categories();app.main.execute(()->{if(getView()!=view)return;String[] labels=new String[categories.size()+1];labels[0]="全部分类";for(int i=0;i<categories.size();i++)labels[i+1]=categories.get(i).name;new androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("选择分类").setItems(labels,(dialog,index)->vm.setFilter(vm.filter().withCategory(index==0?null:categories.get(index-1).id))).show();});});});
  view.findViewById(R.id.ledger_month).setOnClickListener(v->com.example.budgetapp.ui.common.MonthPicker.show(requireContext(),selectedMonth(),YearMonth.now(TimeWindows.LEDGER_ZONE),month->{var range=TimeWindows.month(month);var f=vm.filter();vm.setFilter(new LedgerFilter(range.startMs,range.endMs,f.metric,f.categoryId,f.provider));}));
  int[] filters={R.id.ledger_all,R.id.ledger_expense,R.id.ledger_income};LedgerFilter.Metric[] metrics={LedgerFilter.Metric.NET_OUTFLOW,LedgerFilter.Metric.EXPENSE,LedgerFilter.Metric.INCOME};
  for(int i=0;i<filters.length;i++){var metric=metrics[i];view.findViewById(filters[i]).setOnClickListener(v->{var f=vm.filter();vm.setFilter(new LedgerFilter(f.startMs,f.endMs,metric,f.categoryId,f.provider));});}
  view.findViewById(R.id.ledger_undo).setOnClickListener(v->vm.undo());view.findViewById(R.id.ledger_add).setOnClickListener(v->activity.showEditor(null));view.findViewById(R.id.ledger_review).setOnClickListener(v->activity.showReview());
  vm.snapshot().observe(getViewLifecycleOwner(),snapshot->{
   adapter.setCategoryNames(snapshot.entries);render(view,snapshot);
   var app=((BudgetApplication)activity.getApplication()).container();String categoryId=snapshot.filter.categoryId;
   app.io.execute(()->{String name="分类";for(var category:app.categories.categories())if(category.id.equals(categoryId))name=category.name;String label=name;app.main.execute(()->{if(getView()==view&&Objects.equals(vm.filter().categoryId,categoryId)){categoryLabel=label;render(view,vm.snapshot().getValue());}});});
  });
  vm.rows().observe(getViewLifecycleOwner(),rows->{var snapshot=vm.snapshot().getValue();if(snapshot!=null&&snapshot.valid)adapter.submit(rows);render(view,snapshot);});vm.refresh();
 }
 private YearMonth selectedMonth(){return vm.filter().startMs==Long.MIN_VALUE?YearMonth.now(TimeWindows.LEDGER_ZONE):YearMonth.from(Instant.ofEpochMilli(vm.filter().startMs).atZone(TimeWindows.LEDGER_ZONE));}
 private void render(View view,StatsRepository.Snapshot snapshot){
  if(snapshot==null)return;var f=snapshot.filter;boolean income=f.metric==LedgerFilter.Metric.INCOME;
  int[] filters={R.id.ledger_all,R.id.ledger_expense,R.id.ledger_income};LedgerFilter.Metric[] metrics={LedgerFilter.Metric.NET_OUTFLOW,LedgerFilter.Metric.EXPENSE,LedgerFilter.Metric.INCOME};for(int i=0;i<filters.length;i++)view.findViewById(filters[i]).setSelected(f.metric==metrics[i]);
  boolean custom=isCustomRange(f);
  String monthLabel=f.startMs==Long.MIN_VALUE?"全部月份":Instant.ofEpochMilli(f.startMs).atZone(TimeWindows.LEDGER_ZONE).format(DateTimeFormatter.ofPattern("yyyy年M月"));
  if(custom){var start=Instant.ofEpochMilli(f.startMs).atZone(TimeWindows.LEDGER_ZONE);var end=Instant.ofEpochMilli(Math.max(f.startMs,f.endMs-1)).atZone(TimeWindows.LEDGER_ZONE);var format=DateTimeFormatter.ofPattern(start.getYear()==end.getYear()?"M/d":"yyyy/M/d");monthLabel=start.format(format)+" – "+end.format(format);}
  ((TextView)view.findViewById(R.id.ledger_month)).setText(monthLabel+" ▾");
  TextView category=view.findViewById(R.id.ledger_category);category.setText(categoryLabel+" ▾");category.setSelected(f.categoryId!=null);
  boolean filtered=f.categoryId!=null||!vm.search().isEmpty()||custom||f.provider!=null;view.findViewById(R.id.ledger_filter_scope).setVisibility(filtered?View.VISIBLE:View.GONE);
  List<String> parts=new ArrayList<>();if(f.categoryId!=null)parts.add(categoryLabel);if(!vm.search().isEmpty())parts.add("搜索“"+vm.search()+"”");if(custom)parts.add("指定时间范围");if(f.provider!=null)parts.add("来源筛选");((TextView)view.findViewById(R.id.ledger_filter_description)).setText(String.join(" · ",parts));
  ((TextView)view.findViewById(R.id.ledger_summary_label)).setText(income?"收入合计":"支出合计");TextView total=view.findViewById(R.id.ledger_total);total.setTextColor(requireContext().getColor(income?R.color.obs_income:R.color.obs_ink));
  if(!snapshot.valid){total.setText(R.string.stats_error);adapter.submit(Collections.emptyList());view.findViewById(R.id.ledger_empty).setVisibility(View.GONE);return;}
  List<com.example.budgetapp.data.entity.LedgerTransactionEntity> rows=vm.rows().getValue();if(rows==null)rows=Collections.emptyList();long amount=0;for(var tx:rows)if((income?"INCOME":"EXPENSE").equals(tx.kind))amount=Math.addExact(amount,tx.amountMinor);
  total.setText("¥ "+Money.formatFen(amount));((TextView)view.findViewById(R.id.ledger_count)).setText(rows.size()+" 笔已记账");((TextView)view.findViewById(R.id.ledger_summary_count)).setText("已排除待核对\n"+rows.size()+" 笔记录");view.findViewById(R.id.ledger_empty).setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);view.findViewById(R.id.ledger_undo).setVisibility(vm.canUndo()?View.VISIBLE:View.GONE);
 }
 private boolean isCustomRange(LedgerFilter filter){
  if(filter.startMs==Long.MIN_VALUE)return false;
  var start=Instant.ofEpochMilli(filter.startMs).atZone(TimeWindows.LEDGER_ZONE);var month=YearMonth.from(start);var full=TimeWindows.month(month);
  if(filter.startMs==full.startMs&&filter.endMs==full.endMs)return false;
  Clock clock=((BudgetApplication)requireActivity().getApplication()).container().clock.withZone(TimeWindows.LEDGER_ZONE);var now=clock.instant().atZone(TimeWindows.LEDGER_ZONE);var end=Instant.ofEpochMilli(filter.endMs).atZone(TimeWindows.LEDGER_ZONE);
  boolean currentCutoff=month.equals(YearMonth.from(now))&&filter.startMs==full.startMs&&filter.endMs<=clock.millis()&&end.toLocalDate().equals(now.toLocalDate())&&(!end.toLocalTime().equals(LocalTime.MIDNIGHT)||filter.endMs==clock.millis());
  return !currentCutoff;
 }
}
