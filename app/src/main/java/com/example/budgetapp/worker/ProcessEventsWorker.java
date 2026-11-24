package com.example.budgetapp.worker;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.example.budgetapp.*;
import com.example.budgetapp.data.*;
import com.example.budgetapp.processing.TransactionProcessor;
public final class ProcessEventsWorker extends Worker {
 private AppContainer app;
 private final LedgerDatabase db;private final TransactionProcessor processor;
 public ProcessEventsWorker(@NonNull Context context,@NonNull WorkerParameters parameters){super(context,parameters);app=((BudgetApplication)context.getApplicationContext()).container();db=app.db;processor=app.processor;}
 ProcessEventsWorker(Context context,WorkerParameters parameters,LedgerDatabase db,TransactionProcessor processor){super(context,parameters);this.db=db;this.processor=processor;}
 @NonNull @Override public Result doWork(){try{if(app!=null)app.retention.purgeExpired();while(!isStopped()){var pending=db.sourceEventDao().pending(100);if(pending.isEmpty()){if(app!=null)app.health.onBacklog(0,null);return Result.success();}for(var event:pending){if(isStopped())return Result.retry();var processed=processor.process(event.id);if(BuildConfig.DEBUG)android.util.Log.d("BudgetProcessing","provider="+event.provider+" status="+processed.status);if(app!=null){app.health.onParsed();getApplicationContext().getSharedPreferences("health_metadata",Context.MODE_PRIVATE).edit().putLong("parsed",app.clock.millis()).apply();}}}return Result.retry();}catch(RuntimeException temporary){AppContainer app=((BudgetApplication)getApplicationContext()).container();app.health.onBacklog(0,"PROCESSING_ERROR");return getRunAttemptCount()>=3?Result.failure():Result.retry();}}
}
