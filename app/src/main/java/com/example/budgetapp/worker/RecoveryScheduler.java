package com.example.budgetapp.worker;
import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;
public final class RecoveryScheduler {
 private final Context context;
 public RecoveryScheduler(Context context){this.context=context.getApplicationContext();}
 public void schedule(){OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(ProcessEventsWorker.class).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build();WorkManager.getInstance(context).enqueueUniqueWork("process-source-events",ExistingWorkPolicy.APPEND_OR_REPLACE,request);}
}
