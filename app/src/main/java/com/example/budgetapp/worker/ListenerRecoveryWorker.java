package com.example.budgetapp.worker;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.example.budgetapp.*;
import com.example.budgetapp.capture.notification.*;
import com.example.budgetapp.health.ListenerRecoveryPolicy;
public final class ListenerRecoveryWorker extends Worker {
    public ListenerRecoveryWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
    @NonNull @Override public Result doWork(){
        AppContainer app=((BudgetApplication)getApplicationContext()).container();
        var outcome=ListenerRecoveryPolicy.attempt(new ListenerRecoveryPolicy.Port(){
            public boolean authorized(){boolean value=ObservationSession.isAuthorized(getApplicationContext());app.health.onAuthorization(value);return value;}
            public boolean connected(){return PaymentNotificationService.isListenerConnected();}
            public void request(){app.attemptNotificationRebind(getApplicationContext());}
            public void exhausted(){app.health.onDisconnected();app.health.onBacklog(app.health.snapshot().pendingCount,"LISTENER_RECONNECT_EXHAUSTED");}
        },getRunAttemptCount());
        return outcome==ListenerRecoveryPolicy.Outcome.SUCCESS?Result.success():outcome==ListenerRecoveryPolicy.Outcome.RETRY?Result.retry():Result.failure();
    }
}
