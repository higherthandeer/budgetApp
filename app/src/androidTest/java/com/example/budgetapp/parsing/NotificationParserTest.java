package com.example.budgetapp.parsing;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.capture.notification.NotificationSnapshot;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
import static com.example.budgetapp.parsing.ParseResult.Disposition.*;
@RunWith(AndroidJUnit4.class) public class NotificationParserTest {
 private NotificationParser parser;
 @Before public void setUp() throws Exception {String json=new String(InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("notifications/synthetic_catalog.json").readAllBytes(),StandardCharsets.UTF_8);parser=new NotificationParser(TemplateCatalog.fromJson(json));}
 private NotificationSnapshot snapshot(String body,String channel,boolean truncated,boolean summary,String version){return new NotificationSnapshot("com.tencent.mm","slot",1793491200000L,1793577600000L,channel,"合成交易凭证",body,"",Collections.emptyList(),summary,truncated,version);}
 private String body(){return "合成凭证\n方向:支出\n实付:28.80\n币种:CNY\n状态:成功\n成交日期:2026-10-31\n商家:面馆";}
 @Test public void refundAndChatAreNotPayments(){assertEquals(IGNORED,parser.parse(snapshot("合成凭证\n退款成功 100.00","test-trade",false,false,"test-1")).disposition);assertEquals("REFUND_UNSUPPORTED",parser.parse(snapshot("合成凭证\n退款成功 100.00","test-trade",false,false,"test-1")).reasonCode);assertEquals(NotificationParser.IntakeDecision.DROP,parser.classify(snapshot("支付成功 28.80","chat",false,false,"test-1")));}
 @Test public void postedTimeCannotSupplyTradeDate(){var r=parser.parse(snapshot(body().replace("成交日期:2026-10-31\n",""),"test-trade",false,false,"test-1"));assertEquals(REVIEW,r.disposition);assertNull(r.candidate.occurredAtMs);var day=parser.parse(snapshot(body(),"test-trade",false,false,"test-1"));assertEquals(CANDIDATE,day.disposition);assertEquals(com.example.budgetapp.processing.LedgerTypes.TimePrecision.DAY,day.candidate.timePrecision);assertEquals(Long.valueOf(1793376000000L),day.candidate.occurredAtMs);assertEquals(Long.valueOf(2880),day.candidate.amountMinor);}
 @Test public void acceptsOnlyAnchoredPaidAmountAndSufficientFacts(){var r=parser.parse(snapshot(body()+"\n原价:38.80\n优惠:10.00","test-trade",false,false,"test-1"));assertEquals(Long.valueOf(2880),r.candidate.amountMinor);for(String text:new String[]{body().replace("CNY","USD"),body().replace("状态:成功","状态:不明"),body().replace("实付:28.80","原价:28.80"),body()+"\n实付:30.00"})assertEquals(REVIEW,parser.parse(snapshot(text,"test-trade",false,false,"test-1")).disposition);assertEquals(REVIEW,parser.parse(snapshot(body(),"test-trade",true,false,"test-1")).disposition);assertEquals(REVIEW,parser.parse(snapshot(body(),"test-trade",false,true,"test-1")).disposition);assertEquals(REVIEW,parser.parse(snapshot(body(),"test-trade",false,false,"other-version")).disposition);}
}
