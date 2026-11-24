package com.example.budgetapp.data;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.time.*;
import static org.junit.Assert.*;
import static com.example.budgetapp.data.ManualLedgerRepository.SaveResult.Status.*;
import static com.example.budgetapp.data.ManualLedgerRepository.WriteResult.Status.*;
@RunWith(AndroidJUnit4.class) public class ManualLedgerRepositoryTest {
 private LedgerDatabase db; private ManualLedgerRepository repo;
 @Before public void setUp() {
  db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),LedgerDatabase.class).addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();
  repo=new ManualLedgerRepository(db,Clock.fixed(Instant.parse("2026-11-11T00:00:00Z"),ZoneOffset.UTC));
 }
 @After public void close(){db.close();}
 private TransactionDraft draft(long amount){return new TransactionDraft(Kind.EXPENSE,amount,TimeWindows.dayAnchor(LocalDate.of(2026,11,5)),TimePrecision.DAY,"面馆","WECHAT","scope",null,"");}
 @Test public void submissionDeleteAndUndoAreIdempotentAndRevisionGuarded(){
  var a=repo.save("submission-1",draft(2880),false);
  assertEquals(SAVED,a.status);assertEquals(a.transactionId,repo.save("submission-1",draft(2880),false).transactionId);assertEquals(1,db.ledgerDao().countPosted());
  assertEquals(STALE,repo.delete(a.transactionId,0).status);
  var deleted=repo.delete(a.transactionId,a.revision);assertEquals(APPLIED,deleted.status);assertEquals(0,db.ledgerDao().countPosted());assertEquals("DELETED",db.ledgerDao().find(a.transactionId).bookingState);
  assertEquals(APPLIED,repo.undoDelete(a.transactionId,deleted.revision).status);assertEquals(1,db.ledgerDao().countPosted());
 }
 @Test public void editPreservesAcceptedSourceAndPreviousValues(){
  var a=repo.save("initial",draft(10000),false);String fact=db.sourceEventDao().evidence(a.transactionId).get(0).parsedJson;
  assertEquals(APPLIED,repo.update(a.transactionId,a.revision,draft(8000)).status);
  var tx=db.ledgerDao().find(a.transactionId);assertEquals(8000,tx.amountMinor);assertTrue(tx.recentPreviousValuesJson.contains("10000"));assertEquals(fact,db.sourceEventDao().evidence(a.transactionId).get(0).parsedJson);
  assertEquals(STALE,repo.update(a.transactionId,a.revision,draft(7000)).status);
  var income=new TransactionDraft(Kind.INCOME,9000,TimeWindows.dayAnchor(LocalDate.of(2026,12,1)),TimePrecision.DAY,"","OTHER","scope",null,"");
  assertEquals(APPLIED,repo.update(a.transactionId,tx.revision,income).status);assertEquals(LedgerTypes.UNCATEGORIZED_INCOME,db.ledgerDao().find(a.transactionId).categoryId);
 }
 @Test public void similarRequiresExplicitConfirmation(){
  repo.save("one",draft(2880),false);var warning=repo.save("two",draft(2880),false);assertEquals(DUPLICATE_WARNING,warning.status);assertEquals(1,warning.similarIds.size());assertEquals(1,db.ledgerDao().countPosted());assertEquals(SAVED,repo.save("two",draft(2880),true).status);assertEquals(2,db.ledgerDao().countPosted());
 }
 @Test public void wrongDirectionCategoryAndMergeUndoAreRejected(){
  var wrong=new TransactionDraft(Kind.INCOME,1,100,TimePrecision.DAY,"","OTHER","scope",LedgerTypes.UNCATEGORIZED_EXPENSE,"");
  assertThrows(IllegalArgumentException.class,()->repo.save("wrong",wrong,false));
  var a=repo.save("one",draft(2880),false);db.getOpenHelper().getWritableDatabase().execSQL("UPDATE ledger_transaction SET booking_state='DELETED',deletion_reason='MERGE',revision=2 WHERE id=?",new Object[]{a.transactionId});assertEquals(NOT_ALLOWED,repo.undoDelete(a.transactionId,2).status);
 }
 @Test public void failedEvidenceWriteRollsBackWholeTransaction(){
  db.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER fail_source BEFORE INSERT ON source_event BEGIN SELECT RAISE(ABORT,'controlled failure'); END");
  assertThrows(RuntimeException.class,()->repo.save("fails",draft(2880),false));assertEquals(0,db.ledgerDao().countPosted());assertTrue(db.sourceEventDao().pending(10).isEmpty());
 }
 @Test public void archivedCategoryRejectsNewAssignmentsButPreservesHistoricalEdits(){String food="00000000-0000-0000-0000-000000000003";var d=new TransactionDraft(Kind.EXPENSE,100,100,TimePrecision.DAY,"店","OTHER","scope",food,"");String id=repo.save("before-archive",d,true).transactionId;new CategoryRepository(db,Clock.systemUTC()).archiveCategory(food);assertThrows(IllegalArgumentException.class,()->repo.save("after-archive",d,true));assertEquals(APPLIED,repo.update(id,1,d).status);assertEquals(food,db.ledgerDao().find(id).categoryId);}
}
