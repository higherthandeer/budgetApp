package com.example.budgetapp.ui.review;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.budgetapp.R;
import com.example.budgetapp.data.EventCodec;
import com.example.budgetapp.data.entity.SourceEventEntity;
import com.example.budgetapp.parsing.ParsedTransaction;
import com.example.budgetapp.processing.*;
import java.time.*;
import java.util.*;
public final class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.Row> {
 interface Actions {void link(SourceEventEntity e);void correct(SourceEventEntity e,boolean existing);void ignore(SourceEventEntity e);}
 private List<SourceEventEntity> rows=Collections.emptyList();private final Actions actions;private boolean busy;
 ReviewAdapter(Actions actions){this.actions=actions;}
 void submit(List<SourceEventEntity> rows){this.rows=rows;notifyDataSetChanged();}
 void busy(boolean busy){this.busy=busy;notifyDataSetChanged();}
 @Override public int getItemCount(){return rows.size();}
 @NonNull @Override public Row onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Row(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_review,parent,false));}
 @Override public void onBindViewHolder(@NonNull Row row,int position){SourceEventEntity e=rows.get(position);String reason=e.errorCode==null?e.reviewReasonCodesJson:e.errorCode;row.reason.setText(reason!=null&&(reason.contains("RAW_EXPIRED")||reason.contains("RAW_UNAVAILABLE"))?R.string.retention_review_missing:reason!=null&&reason.contains("DUPLICATE")?R.string.review_reason_duplicate:reason!=null&&reason.contains("CONFLICT")?R.string.review_reason_conflict:R.string.review_reason_missing);ParsedTransaction p=null;try{if(e.candidateJson!=null)p=EventCodec.decodeParsed(e.candidateJson).candidate;}catch(IllegalArgumentException ignored){}String unknown=row.itemView.getContext().getString(R.string.review_missing);row.summary.setText(row.itemView.getContext().getString(R.string.review_summary,p==null||p.merchant==null?row.itemView.getContext().getString(R.string.review_unknown_merchant):p.merchant,p==null||p.amountMinor==null?unknown:Money.formatFen(p.amountMinor),p==null||p.occurredAtMs==null?unknown:Instant.ofEpochMilli(p.occurredAtMs).atZone(TimeWindows.LEDGER_ZONE).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));for(int id:new int[]{R.id.review_link,R.id.review_new,R.id.review_ignore,R.id.review_correct})row.itemView.findViewById(id).setEnabled(!busy);row.itemView.findViewById(R.id.review_link).setVisibility(reason!=null&&(reason.contains("DUPLICATE")||reason.contains("CONFLICT"))?View.VISIBLE:View.GONE);row.itemView.findViewById(R.id.review_link).setOnClickListener(v->actions.link(e));row.itemView.findViewById(R.id.review_new).setOnClickListener(v->actions.correct(e,false));row.itemView.findViewById(R.id.review_correct).setVisibility(e.transactionId==null&&(e.reviewCandidatesJson==null||e.reviewCandidatesJson.equals("[]"))?View.GONE:View.VISIBLE);row.itemView.findViewById(R.id.review_correct).setOnClickListener(v->actions.correct(e,true));row.itemView.findViewById(R.id.review_ignore).setOnClickListener(v->actions.ignore(e));}
 static final class Row extends RecyclerView.ViewHolder{final TextView reason,summary;Row(View view){super(view);reason=view.findViewById(R.id.review_reason);summary=view.findViewById(R.id.review_summary);}}
}
