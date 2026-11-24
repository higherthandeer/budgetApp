package com.example.budgetapp.data;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.capture.notification.NotificationSnapshot;
import com.example.budgetapp.parsing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class EventCodecTest {
 @Test public void snapshotAndCandidateRoundTrip(){var s=new NotificationSnapshot("com.tencent.mm","key",1,2,"channel","title","text","big",Arrays.asList("one","two"),false,false,"1");var copy=EventCodec.decodeSnapshot(EventCodec.encodeSnapshot(s));assertEquals(s.textLines,copy.textLines);assertEquals(s.capturedAtMs,copy.capturedAtMs);assertEquals(s.notificationKey,copy.notificationKey);var p=new ParsedTransaction();p.kind=Kind.EXPENSE;p.amountMinor=2880L;p.currency="CNY";p.timePrecision=TimePrecision.DAY;p.occurredAtMs=1793376000000L;p.tradeStatus=ParsedTransaction.TradeStatus.SUCCESS;p.proofs.add(ParsedTransaction.Proof.DATE);p.identities.add(new ParsedTransaction.Identity("ORDER","platform","12345678901234567890"));var result=new ParseResult(ParseResult.Disposition.CANDIDATE,p,"OK","synthetic",1);var decoded=EventCodec.decodeParsed(EventCodec.encodeParsed(result));assertEquals(p.amountMinor,decoded.candidate.amountMinor);assertEquals(p.proofs,decoded.candidate.proofs);assertEquals("12345678901234567890",decoded.candidate.identities.get(0).externalId);assertNull(decoded.candidate.merchant);}
 @Test public void rejectsBadVersionsAndPayloads(){for(String text:new String[]{"","{}","{\"formatVersion\":9}"}){assertThrows(IllegalArgumentException.class,()->EventCodec.decodeSnapshot(text));assertThrows(IllegalArgumentException.class,()->EventCodec.decodeParsed(text));}assertThrows(IllegalArgumentException.class,()->TemplateCatalog.fromJson("{\"formatVersion\":2,\"templates\":[]}"));}
 @Test public void oldCandidatesWithoutDateBasisRemainReadable() throws Exception {var p=new ParsedTransaction();p.occurredAtMs=1L;var result=new ParseResult(ParseResult.Disposition.REVIEW,p,"MISSING_FACTS","legacy",1);var encoded=new org.json.JSONObject(EventCodec.encodeParsed(result));encoded.getJSONObject("candidate").remove("dateBasis");assertEquals(ParsedTransaction.DateBasis.TRANSACTION_TEXT,EventCodec.decodeParsed(encoded.toString()).candidate.dateBasis);encoded.getJSONObject("candidate").put("dateBasis","UNKNOWN_DATE");assertThrows(IllegalArgumentException.class,()->EventCodec.decodeParsed(encoded.toString()));}
}
