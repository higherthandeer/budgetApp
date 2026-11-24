package com.example.budgetapp.worker;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.*;
import androidx.work.testing.*;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class ListenerRecoveryTest {
 @Test public void requestsUseOneDurableNonPeriodicJobAndCanBeCancelled() throws Exception {
  var context=ApplicationProvider.getApplicationContext();WorkManagerTestInitHelper.initializeTestWorkManager(context,new Configuration.Builder().setExecutor(command->{}).build());var manager=WorkManager.getInstance(context);var scheduler=new ListenerRecoveryScheduler(context);scheduler.schedule();scheduler.schedule();var jobs=manager.getWorkInfosForUniqueWork(ListenerRecoveryScheduler.NAME).get(10,TimeUnit.SECONDS);assertEquals(1,jobs.size());assertEquals(WorkInfo.State.ENQUEUED,jobs.get(0).getState());assertTrue(jobs.get(0).getTags().contains(ListenerRecoveryWorker.class.getName()));var id=jobs.get(0).getId();new ListenerRecoveryScheduler(context).schedule();assertEquals(id,manager.getWorkInfosForUniqueWork(ListenerRecoveryScheduler.NAME).get().get(0).getId());scheduler.cancel();assertEquals(WorkInfo.State.CANCELLED,manager.getWorkInfosForUniqueWork(ListenerRecoveryScheduler.NAME).get().get(0).getState());
 }
 @Test public void workerWithoutPermissionFinishesEvenAtLastAttempt(){var context=ApplicationProvider.getApplicationContext();var worker=TestWorkerBuilder.from(context,ListenerRecoveryWorker.class,Runnable::run).setRunAttemptCount(4).build();assertEquals(ListenableWorker.Result.success(),worker.doWork());}
}
