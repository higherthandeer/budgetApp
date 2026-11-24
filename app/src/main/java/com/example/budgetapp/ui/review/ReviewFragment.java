package com.example.budgetapp.ui.review;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.example.budgetapp.*;
import com.example.budgetapp.data.*;
import com.example.budgetapp.data.entity.*;
import com.example.budgetapp.parsing.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import java.time.*;
import java.util.*;

public final class ReviewFragment extends Fragment implements ReviewAdapter.Actions {
 private ReviewViewModel vm;
 public ReviewFragment(){super(R.layout.fragment_review);}
 @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state){vm=((MainActivity)requireActivity()).reviewViewModel();RecyclerView list=view.findViewById(R.id.review_list);list.setLayoutManager(new LinearLayoutManager(requireContext()));ReviewAdapter adapter=new ReviewAdapter(this);list.setAdapter(adapter);vm.rows().observe(getViewLifecycleOwner(),rows->{adapter.submit(rows);view.findViewById(R.id.review_empty).setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);});vm.busy().observe(getViewLifecycleOwner(),busy->adapter.busy(Boolean.TRUE.equals(busy)));vm.error.observe(getViewLifecycleOwner(),error->((TextView)view.findViewById(R.id.review_error)).setText(error==null?"":getString(error)));view.findViewById(R.id.review_merge).setOnClickListener(v->merge());}
 private String label(LedgerTransactionEntity tx){return getString(R.string.review_summary,tx.merchantRaw.isEmpty()?getString(R.string.review_unknown_merchant):tx.merchantRaw,Money.formatFen(tx.amountMinor),Instant.ofEpochMilli(tx.occurredAtMs).atZone(TimeWindows.LEDGER_ZONE).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))+("DELETED".equals(tx.bookingState)?" · "+getString(R.string.review_deleted):"");}
 @Override public void link(SourceEventEntity e){vm.candidates(e,rows->{if(!isAdded())return;if(rows.isEmpty()){Toast.makeText(requireContext(),R.string.review_link_none,Toast.LENGTH_LONG).show();return;}String[] labels=new String[rows.size()];for(int i=0;i<labels.length;i++)labels[i]=label(rows.get(i));int[] selected={0};new AlertDialog.Builder(requireContext()).setTitle(R.string.review_link).setSingleChoiceItems(labels,0,(d,which)->selected[0]=which).setNegativeButton(R.string.entry_cancel,null).setPositiveButton(R.string.review_link_confirm,(d,w)->vm.run(()->vm.app.review.linkExisting(e.id,rows.get(selected[0]).id))).show();});}
 @Override public void ignore(SourceEventEntity e){new AlertDialog.Builder(requireContext()).setMessage(R.string.review_ignore_confirm).setNegativeButton(R.string.entry_cancel,null).setPositiveButton(R.string.review_ignore,(d,w)->vm.run(()->vm.app.review.ignore(e.id))).show();}
 @Override public void correct(SourceEventEntity e,boolean existing){if(existing){vm.candidates(e,rows->{if(!isAdded())return;for(var tx:rows)if(tx.id.equals(e.transactionId)){form(e,tx);return;}if(!rows.isEmpty()){String[] labels=new String[rows.size()];for(int i=0;i<labels.length;i++)labels[i]=label(rows.get(i));new AlertDialog.Builder(requireContext()).setTitle(R.string.review_correct).setItems(labels,(d,index)->form(e,rows.get(index))).show();}else vm.error.setValue(R.string.review_error);});}else form(e,null);}
 private void form(SourceEventEntity e,LedgerTransactionEntity tx) {
  View form=getLayoutInflater().inflate(R.layout.dialog_review_resolution,null);
  EditText amount=form.findViewById(R.id.review_amount),date=form.findViewById(R.id.review_date),merchant=form.findViewById(R.id.review_merchant);
  Spinner kind=form.findViewById(R.id.review_kind);
  kind.setAdapter(new ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_dropdown_item,new String[]{getString(R.string.entry_expense),getString(R.string.entry_income)}));
  ParsedTransaction parsed=null;
  try {if(e.candidateJson!=null)parsed=EventCodec.decodeParsed(e.candidateJson).candidate;}
  catch(IllegalArgumentException ignored) {}
  final ParsedTransaction observed=parsed;
  if(observed!=null) {
   if(observed.amountMinor!=null)amount.setText(Money.formatFen(observed.amountMinor));
   if(observed.occurredAtMs!=null)date.setText(Instant.ofEpochMilli(observed.occurredAtMs).atZone(TimeWindows.LEDGER_ZONE).toLocalDate().toString());
   merchant.setText(observed.merchant);
   kind.setSelection(observed.kind==Kind.INCOME?1:0);
  }
  AlertDialog dialog=new AlertDialog.Builder(requireContext()).setTitle(tx==null?getString(R.string.review_new):label(tx)).setView(form).setNegativeButton(R.string.entry_cancel,null).setPositiveButton(R.string.category_apply,null).create();
  dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   try {
    LocalDate selectedDate=LocalDate.parse(date.getText().toString());
    long occurredAt=TimeWindows.dayAnchor(selectedDate);
    TimePrecision precision=TimePrecision.DAY;
    if(observed!=null&&observed.occurredAtMs!=null&&observed.timePrecision!=null
      &&selectedDate.equals(Instant.ofEpochMilli(observed.occurredAtMs).atZone(TimeWindows.LEDGER_ZONE).toLocalDate())) {
     occurredAt=observed.occurredAtMs;
     precision=observed.timePrecision;
    }
    TransactionDraft corrected=new TransactionDraft(kind.getSelectedItemPosition()==0?Kind.EXPENSE:Kind.INCOME,Money.parseFen(amount.getText().toString()),occurredAt,precision,merchant.getText().toString(),e.provider,e.accountScopeId,null,tx==null?"":tx.note);
    vm.run(()->tx==null?vm.app.review.confirmNew(e.id,corrected):vm.app.review.acceptCorrection(e.id,tx.id,tx.revision,corrected));
    dialog.dismiss();
   } catch(RuntimeException invalid) {amount.setError(getString(R.string.entry_invalid));}
  }));
  dialog.show();
 }
 private void merge(){vm.posted(rows->{if(!isAdded())return;if(rows.size()<2){vm.error.setValue(R.string.review_link_none);return;}String[] labels=new String[rows.size()];for(int i=0;i<labels.length;i++)labels[i]=label(rows.get(i));int[] primary={0};new AlertDialog.Builder(requireContext()).setTitle(R.string.review_merge).setSingleChoiceItems(labels,0,(d,index)->primary[0]=index).setNegativeButton(R.string.entry_cancel,null).setPositiveButton(R.string.category_preview,(d,w)->{LedgerTransactionEntity p=rows.get(primary[0]);List<LedgerTransactionEntity> others=new ArrayList<>();for(var t:rows)if(!t.id.equals(p.id)&&t.kind.equals(p.kind)&&t.currency.equals(p.currency))others.add(t);if(others.isEmpty()){vm.error.setValue(R.string.review_error);return;}String[] choices=new String[others.size()];for(int i=0;i<choices.length;i++)choices[i]=label(others.get(i));int[] duplicate={0};new AlertDialog.Builder(requireContext()).setTitle(label(p)).setSingleChoiceItems(choices,0,(dialog,index)->duplicate[0]=index).setNegativeButton(R.string.entry_cancel,null).setPositiveButton(R.string.category_preview,(dialog,which)->{var q=others.get(duplicate[0]);new AlertDialog.Builder(requireContext()).setTitle(R.string.review_merge_confirm).setMessage(label(p)+"\n"+label(q)).setNegativeButton(R.string.entry_cancel,null).setPositiveButton(R.string.category_apply,(confirm,ignored)->vm.run(()->vm.app.review.merge(p.id,p.revision,q.id,q.revision))).show();}).show();}).show();});}
}
