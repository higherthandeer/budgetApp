package com.example.budgetapp.capture.notification;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.*;
import androidx.work.testing.WorkManagerTestInitHelper;
import com.example.budgetapp.data.*;
import com.example.budgetapp.parsing.*;
import com.example.budgetapp.worker.RecoveryScheduler;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class EventIntakeTest {
 @Test public void chatDoesNotReachDisk() throws Exception {
  var context=ApplicationProvider.getApplicationContext();var db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase.class).addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();ExecutorService io=Executors.newSingleThreadExecutor();
  try{WorkManagerTestInitHelper.initializeTestWorkManager(context,new Configuration.Builder().setExecutor(command->{}).build());String json=new String(InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("notifications/synthetic_catalog.json").readAllBytes(),StandardCharsets.UTF_8);var intake=new EventIntake(db,new NotificationParser(TemplateCatalog.fromJson(json)),new LocalSettings(context),new RecoveryScheduler(context),io);
   intake.accept(snapshot("chat","支付成功 28.80"));io.submit(()->{}).get();assertTrue(db.sourceEventDao().pending(10).isEmpty());
   var s=snapshot("test-trade","合成凭证\n实付:28.80");intake.accept(s);intake.accept(s);io.submit(()->{}).get();assertEquals(1,db.sourceEventDao().pending(10).size());assertTrue(db.sourceEventDao().pending(10).get(0).eventKey.startsWith("v1:"));
  }finally{io.shutdownNow();db.close();}
 }
 private NotificationSnapshot snapshot(String channel,String text){return new NotificationSnapshot("com.tencent.mm","slot",100,200,channel,"合成交易凭证",text,"",Collections.emptyList(),false,false,"test-1");}
}
