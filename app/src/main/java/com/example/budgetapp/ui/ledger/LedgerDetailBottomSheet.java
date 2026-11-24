package com.example.budgetapp.ui.ledger;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;
import com.example.budgetapp.*;
import com.example.budgetapp.data.ManualLedgerRepository;
import com.example.budgetapp.processing.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public final class LedgerDetailBottomSheet extends BottomSheetDialogFragment {
 public static void show(MainActivity activity,String id){
  if(activity.getSupportFragmentManager().isStateSaved()||activity.getSupportFragmentManager().findFragmentByTag("ledger_detail")!=null)return;
  LedgerDetailBottomSheet sheet=new LedgerDetailBottomSheet();Bundle args=new Bundle();args.putString("id",id);sheet.setArguments(args);sheet.show(activity.getSupportFragmentManager(),"ledger_detail");
 }
 @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup parent,@Nullable Bundle state){
  LinearLayout root=new LinearLayout(requireContext());root.setOrientation(LinearLayout.VERTICAL);int padding=Math.round(24*getResources().getDisplayMetrics().density);root.setPadding(padding,padding,padding,padding);root.setBackgroundColor(requireContext().getColor(R.color.obs_paper));
  label(root,"账单详情",22);MainActivity activity=(MainActivity)requireActivity();AppContainer app=((BudgetApplication)activity.getApplication()).container();String id=requireArguments().getString("id");
  app.io.execute(()->{var tx=app.db.ledgerDao().find(id);var categories=app.categories.categories();app.main.execute(()->{if(!isAdded()||getView()!=root||tx==null)return;
   label(root,("INCOME".equals(tx.kind)?"+":"")+"¥ "+Money.formatFen(tx.amountMinor),36);
   label(root,tx.merchantRaw.isEmpty()?"未填写商家":tx.merchantRaw,18);
   label(root,"时间     "+DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss").withZone(TimeWindows.LEDGER_ZONE).format(Instant.ofEpochMilli(tx.occurredAtMs)),13);
   String category="未分类";for(var item:categories)if(item.id.equals(tx.categoryId))category=item.name;
   label(root,"分类     "+category,13);label(root,"备注     "+(tx.note.isEmpty()?"无":tx.note),13);
   label(root,"来源     "+("WECHAT".equals(tx.provider)?"微信通知":"ALIPAY".equals(tx.provider)?"支付宝通知":"手工记账"),13);
   Button edit=new com.google.android.material.button.MaterialButton(requireContext());edit.setId(R.id.detail_edit);edit.setText("编辑账单");root.addView(edit);edit.setOnClickListener(v->{dismiss();activity.showEditor(id);});
   Button delete=new com.google.android.material.button.MaterialButton(requireContext());delete.setText("删除账单");delete.setTextColor(requireContext().getColor(R.color.obs_red));root.addView(delete);
   delete.setOnClickListener(v->new AlertDialog.Builder(requireContext()).setMessage(R.string.delete_message).setNegativeButton(R.string.entry_cancel,null).setPositiveButton(R.string.delete_confirm,(dialog,which)->{delete.setEnabled(false);app.io.execute(()->{try{var result=app.repository.delete(id,tx.revision);app.main.execute(()->{if(!isAdded())return;if(result.status!=ManualLedgerRepository.WriteResult.Status.APPLIED){delete.setEnabled(true);Toast.makeText(activity,R.string.entry_stale,Toast.LENGTH_LONG).show();return;}dismiss();Snackbar.make(activity.findViewById(android.R.id.content),"账单已删除",Snackbar.LENGTH_LONG).setAction("撤销",undo->app.io.execute(()->{var restored=app.repository.undoDelete(id,result.revision);if(restored.status!=ManualLedgerRepository.WriteResult.Status.APPLIED)app.main.execute(()->Toast.makeText(activity,R.string.entry_stale,Toast.LENGTH_LONG).show());})).show();});}catch(RuntimeException failure){app.main.execute(()->{if(isAdded()){delete.setEnabled(true);Toast.makeText(activity,R.string.entry_failed,Toast.LENGTH_LONG).show();}});}});}).show());
  });});return root;
 }
 private void label(LinearLayout root,String value,int size){TextView text=new TextView(requireContext());text.setText(value);text.setTextSize(size);text.setTextColor(requireContext().getColor(R.color.obs_ink));text.setPadding(0,12,0,12);root.addView(text);}
}
