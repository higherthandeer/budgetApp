package com.example.budgetapp.ui.common;
import android.content.Context;
import android.widget.*;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.example.budgetapp.R;
import java.time.YearMonth;
import java.util.function.Consumer;
public final class MonthPicker {
 private MonthPicker(){}
 public static void show(Context context,YearMonth selected,YearMonth latest,Consumer<YearMonth> onSelect){
  BottomSheetDialog dialog=new BottomSheetDialog(context);
  LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(21*context.getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);root.setBackgroundColor(context.getColor(R.color.obs_paper));
  TextView title=new TextView(context);title.setText("选择月份");title.setTextSize(22);title.setTextColor(context.getColor(R.color.obs_ink));root.addView(title);
  LinearLayout years=new LinearLayout(context);Button previous=new Button(context),next=new Button(context);TextView year=new TextView(context);year.setGravity(17);previous.setText("‹");next.setText("›");years.addView(previous);years.addView(year,new LinearLayout.LayoutParams(0,64,1));years.addView(next);root.addView(years);
  GridLayout grid=new GridLayout(context);grid.setColumnCount(3);root.addView(grid);int[] current={selected.getYear()};
  Runnable render=()->{year.setText(current[0]+"年");next.setEnabled(current[0]<latest.getYear());grid.removeAllViews();for(int m=1;m<=12;m++){YearMonth value=YearMonth.of(current[0],m);Button button=new Button(context);button.setText(m+"月");button.setEnabled(!value.isAfter(latest));button.setTextColor(context.getColor(value.equals(selected)?R.color.obs_green:R.color.obs_muted));GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.columnSpec=GridLayout.spec((m-1)%3,1f);button.setLayoutParams(lp);button.setOnClickListener(v->{onSelect.accept(value);dialog.dismiss();});grid.addView(button);}};
  previous.setOnClickListener(v->{current[0]--;render.run();});next.setOnClickListener(v->{current[0]++;render.run();});render.run();dialog.setContentView(root);dialog.show();
 }
}
