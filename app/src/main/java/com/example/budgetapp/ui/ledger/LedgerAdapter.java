package com.example.budgetapp.ui.ledger;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.budgetapp.R;
import com.example.budgetapp.data.entity.LedgerTransactionEntity;
import com.example.budgetapp.processing.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
public final class LedgerAdapter extends RecyclerView.Adapter<LedgerAdapter.Row> {
 private LedgerFilter.Metric metric=LedgerFilter.Metric.NET_OUTFLOW;
 public void setMetric(LedgerFilter.Metric metric){this.metric=metric;}
 private Map<String,String> categoryNames=Collections.emptyMap(),categoryIcons=Collections.emptyMap();
 public void setCategoryNames(List<com.example.budgetapp.stats.StatsRepository.Entry> entries){Map<String,String> names=new HashMap<>();Map<String,String> icons=new HashMap<>();for(var entry:entries){names.put(entry.transaction.categoryId,entry.categoryName);icons.put(entry.transaction.categoryId,entry.categoryIconKey);}categoryNames=names;categoryIcons=icons;notifyDataSetChanged();}
 private List<LedgerTransactionEntity> rows=Collections.emptyList();private final Consumer<String> select;
 public LedgerAdapter(Consumer<String> select){this.select=select;}
 public void submit(List<LedgerTransactionEntity> rows){this.rows=new ArrayList<>(rows);notifyDataSetChanged();}
 @NonNull @Override public Row onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Row(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ledger,parent,false));}
 @Override public int getItemCount(){return rows.size();}
 @Override public void onBindViewHolder(@NonNull Row row,int position){LedgerTransactionEntity tx=rows.get(position);bindTransaction(row.itemView,tx,metric==LedgerFilter.Metric.NET_OUTFLOW,categoryNames.get(tx.categoryId),categoryIcons.get(tx.categoryId));TextView day=row.itemView.findViewById(R.id.row_day);String date=day(tx);boolean first=position==0||!date.equals(day(rows.get(position-1)));day.setVisibility(first?View.VISIBLE:View.GONE);day.setText(date);row.itemView.setOnClickListener(v->select.accept(tx.id));}
 private static String day(LedgerTransactionEntity tx){return Instant.ofEpochMilli(tx.occurredAtMs).atZone(TimeWindows.LEDGER_ZONE).toLocalDate().toString();}
 public static void bindTransaction(View view,LedgerTransactionEntity tx,boolean net){bindTransaction(view,tx,net,null);}
 public static void bindTransaction(View view,LedgerTransactionEntity tx,boolean net,String categoryName){bindTransaction(view,tx,net,categoryName,com.example.budgetapp.data.CategoryIconCatalog.defaultForName(categoryName));}
 public static void bindTransaction(View view,LedgerTransactionEntity tx,boolean net,String categoryName,String iconKey){
  ((TextView)view.findViewById(R.id.row_merchant)).setText(categoryName==null||categoryName.isEmpty()?"未分类":categoryName);
  TextView amount=view.findViewById(R.id.row_amount);boolean income="INCOME".equals(tx.kind);
  amount.setText((income?"+":"−")+"¥ "+Money.formatFen(tx.amountMinor));amount.setTextColor(view.getContext().getColor(income?R.color.obs_income:R.color.obs_ink));
  ((TextView)view.findViewById(R.id.row_date)).setText(Instant.ofEpochMilli(tx.occurredAtMs).atZone(TimeWindows.LEDGER_ZONE).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))+" · "+(tx.merchantRaw.isEmpty()?"未填写商家":tx.merchantRaw)+(tx.note.isEmpty()?"":" · "+tx.note));
  view.findViewById(R.id.row_day).setVisibility(View.GONE);
  int icon=com.example.budgetapp.ui.common.CategoryIcons.forKey(iconKey);
  ((android.widget.ImageView)view.findViewById(R.id.row_icon)).setImageResource(icon);
  ((android.widget.ImageView)view.findViewById(R.id.row_icon)).setImageTintList(android.content.res.ColorStateList.valueOf(view.getContext().getColor(R.color.obs_green)));

 }

 static final class Row extends RecyclerView.ViewHolder {final TextView amount,merchant,date;Row(View v){super(v);amount=v.findViewById(R.id.row_amount);merchant=v.findViewById(R.id.row_merchant);date=v.findViewById(R.id.row_date);}}
}
