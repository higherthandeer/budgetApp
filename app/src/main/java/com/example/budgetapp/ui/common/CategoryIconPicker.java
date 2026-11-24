package com.example.budgetapp.ui.common;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.example.budgetapp.R;
import com.example.budgetapp.data.CategoryIconCatalog;
import java.util.*;
import java.util.function.Consumer;

/** Selection is staged locally; only the confirmation button commits it. */
public final class CategoryIconPicker {
 private CategoryIconPicker() {}
 public static AlertDialog show(Context context,String initial,Consumer<String> onSelected) {
  String[] selected={CategoryIconCatalog.contains(initial)?initial:"category"};
  LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);
  int gap=Math.round(12*context.getResources().getDisplayMetrics().density);root.setPadding(gap,gap,gap,gap);
  EditText search=new EditText(context);search.setSingleLine(true);search.setHint("搜索图标，如咖啡、交通");root.addView(search);
  ScrollView scroll=new ScrollView(context);LinearLayout content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);
  root.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(context.getResources().getDisplayMetrics().heightPixels/2,Math.round(380*context.getResources().getDisplayMetrics().density))));
  List<MaterialButton> buttons=new ArrayList<>();
  Runnable render=()->{
   content.removeAllViews();buttons.clear();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
   String currentGroup="";GridLayout grid=null;int index=0;
   for(CategoryIconCatalog.Icon item:CategoryIconCatalog.ALL){
    if(!(item.label+" "+item.group+" "+item.key).toLowerCase(Locale.ROOT).contains(query))continue;
    if(!item.group.equals(currentGroup)){
     TextView heading=new TextView(context);heading.setText(item.group);heading.setTextColor(context.getColor(R.color.obs_ink));heading.setPadding(0,gap,0,0);content.addView(heading);
     grid=new GridLayout(context);grid.setColumnCount(3);content.addView(grid);currentGroup=item.group;index=0;
    }
    MaterialButton button=new MaterialButton(context);button.setText(item.label);button.setTextSize(12);button.setTag(item.key);button.setContentDescription("图标："+item.label);
    button.setIconResource(CategoryIcons.forKey(item.key));button.setIconGravity(MaterialButton.ICON_GRAVITY_TOP);button.setIconSize(Math.round(26*context.getResources().getDisplayMetrics().density));button.setCheckable(true);button.setChecked(item.key.equals(selected[0]));
    button.setTextColor(context.getColor(R.color.obs_green));button.setIconTint(ColorStateList.valueOf(context.getColor(R.color.obs_green)));
    button.setBackgroundTintList(ColorStateList.valueOf(context.getColor(button.isChecked()?R.color.obs_mint:R.color.obs_paper)));
    GridLayout.LayoutParams params=new GridLayout.LayoutParams(GridLayout.spec(index/3),GridLayout.spec(index%3,1f));params.width=0;params.height=Math.round(88*context.getResources().getDisplayMetrics().density);params.setMargins(2,2,2,2);grid.addView(button,params);index++;buttons.add(button);
    button.setOnClickListener(v->{selected[0]=item.key;for(MaterialButton b:buttons){boolean checked=selected[0].equals(b.getTag());b.setChecked(checked);b.setBackgroundTintList(ColorStateList.valueOf(context.getColor(checked?R.color.obs_mint:R.color.obs_paper)));}});
   }
   if(buttons.isEmpty()){TextView empty=new TextView(context);empty.setText("没有匹配的图标");empty.setPadding(0,gap,0,gap);content.addView(empty);}
  };
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){render.run();}public void afterTextChanged(Editable e){}});render.run();
  AlertDialog dialog=new AlertDialog.Builder(context).setTitle("选择分类图标").setView(root).setNegativeButton("取消",null).setPositiveButton("使用此图标",(d,w)->onSelected.accept(selected[0])).create();
  dialog.show();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);return dialog;
 }
}
