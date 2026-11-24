package com.example.budgetapp.data;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.data.entity.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.parsing.*;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static com.example.budgetapp.data.ManualLedgerRepository.WriteResult.Status.*;
@RunWith(AndroidJUnit4.class) public class ReviewRepositoryTest {
 private LedgerDatabase db;private ReviewRepository review;private ManualLedgerRepository manual;
 @Before public void setUp(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),LedgerDatabase.class).addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();review=new ReviewRepository(db,Clock.systemUTC());manual=new ManualLedgerRepository(db,Clock.systemUTC());}
 @After public void close(){db.close();}
 private TransactionDraft draft(){return new TransactionDraft(Kind.EXPENSE,2880,1793376000000L,TimePrecision.DAY,"面馆","WECHAT","scope",null,"");}
 private String event(String key){SourceEventEntity e=new SourceEventEntity();e.id=key;e.sourceType="NOTIFICATION";e.eventKey=key;e.provider="WECHAT";e.accountScopeId="scope";e.processState="REVIEW";e.reviewReasonCodesJson="[\"MISSING_DATE\"]";var p=new ParsedTransaction();p.kind=Kind.EXPENSE;p.amountMinor=2880L;e.candidateJson=EventCodec.encodeParsed(new ParseResult(ParseResult.Disposition.REVIEW,p,"MISSING_DATE","synthetic",1));db.sourceEventDao().insertIgnore(e);return key;}
 @Test public void reviewResolutionIsIdempotentAndMergeCannotBeUndoneAsDelete(){String e=event("review");assertEquals(APPLIED,review.confirmNew(e,draft()).status);assertEquals(APPLIED,review.confirmNew(e,draft()).status);assertEquals(1,db.ledgerDao().countPosted());String primary=db.sourceEventDao().find(e).transactionId;String duplicate=manual.save("duplicate",draft(),true).transactionId;assertEquals(APPLIED,review.linkExisting(event("link"),primary).status);assertEquals(2,db.ledgerDao().countPosted());assertEquals(APPLIED,review.merge(primary,1,duplicate,1).status);var deleted=db.ledgerDao().find(duplicate);assertEquals("DELETED",deleted.bookingState);assertEquals("MERGE",deleted.deletionReason);assertEquals(primary,deleted.mergedIntoId);assertEquals(NOT_ALLOWED,manual.undoDelete(duplicate,deleted.revision).status);assertEquals(1,db.ledgerDao().countPosted());}
 @Test public void identityConflictCannotBeConfirmedAsAnotherTransaction(){String id=manual.save("existing",draft(),false).transactionId;TransactionIdentityEntity identity=new TransactionIdentityEntity();identity.id="identity";identity.transactionId=id;identity.provider="WECHAT";identity.accountScopeId="scope";identity.idKind="ORDER";identity.idNamespace="platform";identity.externalId="same";db.ledgerDao().insertIdentity(identity);String e=event("conflict");var row=db.sourceEventDao().find(e);var parsed=EventCodec.decodeParsed(row.candidateJson);parsed.candidate.identities.add(new ParsedTransaction.Identity("ORDER","platform","same"));row.candidateJson=EventCodec.encodeParsed(parsed);db.sourceEventDao().update(row);assertEquals(NOT_ALLOWED,review.confirmNew(e,draft()).status);assertEquals(1,db.ledgerDao().countPosted());manual.delete(id,1);assertEquals(APPLIED,review.linkExisting(e,id).status);assertEquals("DELETED",db.ledgerDao().find(id).bookingState);}
 @Test public void correctionPreservesObservedFactsAndRejectsStale(){String id=manual.save("original",draft(),false).transactionId;String e=event("correction");String observed=db.sourceEventDao().find(e).candidateJson;assertEquals(STALE,review.acceptCorrection(e,id,0,draft()).status);assertEquals(APPLIED,review.acceptCorrection(e,id,1,draft()).status);var source=db.sourceEventDao().find(e);assertEquals(observed,source.parsedJson);assertNotNull(source.correctedCandidateJson);assertTrue(review.listOpen().isEmpty());}
 @Test public void mergeMovesEvidenceAndIdentitiesAtomically(){String primary=manual.save("p",draft(),true).transactionId,duplicate=manual.save("d",draft(),true).transactionId;var identity=new TransactionIdentityEntity();identity.id="id";identity.transactionId=duplicate;identity.provider="WECHAT";identity.accountScopeId="scope";identity.idKind="ORDER";identity.idNamespace="platform";identity.externalId="dup";db.ledgerDao().insertIdentity(identity);assertEquals(STALE,review.merge(primary,0,duplicate,1).status);assertEquals(APPLIED,review.merge(primary,1,duplicate,1).status);assertEquals(2,db.sourceEventDao().evidence(primary).size());assertEquals(primary,db.ledgerDao().identity("WECHAT","scope","ORDER","platform","dup").transactionId);assertEquals(NOT_ALLOWED,review.merge(primary,2,primary,2).status);}
}
