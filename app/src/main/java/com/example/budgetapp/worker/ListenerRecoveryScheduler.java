package com.example.budgetapp.worker;
import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;
public final class ListenerRecoveryScheduler {
    public static final String NAME="recover-notification-listener";
    private final Context context;
    public ListenerRecoveryScheduler(Context context){this.context=context.getApplicationContext();}
    public void schedule(){WorkManager.getInstance(context).enqueueUniqueWork(NAME,ExistingWorkPolicy.KEEP,new OneTimeWorkRequest.Builder(ListenerRecoveryWorker.class).setInitialDelay(30,TimeUnit.SECONDS).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build());}
    public void cancel(){WorkManager.getInstance(context).cancelUniqueWork(NAME);}
}
