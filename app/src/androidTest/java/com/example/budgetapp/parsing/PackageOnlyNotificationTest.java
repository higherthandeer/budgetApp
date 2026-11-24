package com.example.budgetapp.parsing;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.capture.notification.NotificationSnapshot;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class PackageOnlyNotificationTest {
 private NotificationSnapshot sample(String pkg,String body,boolean summary){return new NotificationSnapshot(pkg,"slot",1,1794571200000L,"mipush|changed|100060","任意标题",body,"",Collections.emptyList(),summary,false,"future-version");}
 @Test public void bothPaymentAppsIgnoreChannelVersionAndTitle(){var parser=new NotificationParser(TemplateCatalog.load(ApplicationProvider.getApplicationContext()));for(var s:new NotificationSnapshot[]{sample("com.tencent.mm","已支付¥27.60",false),sample("com.eg.android.AlipayGphone","你有一笔14.21元的支出，点此查看详情。",false)})assertEquals(ParseResult.Disposition.CANDIDATE,parser.parse(s).disposition);}
 @Test public void otherPackagesAndOrdinaryChatRemainIgnored(){var parser=new NotificationParser(TemplateCatalog.load(ApplicationProvider.getApplicationContext()));assertEquals(NotificationParser.IntakeDecision.DROP,parser.classify(sample("other.app","已支付¥27.60",false)));assertEquals(NotificationParser.IntakeDecision.DROP,parser.classify(sample("com.tencent.mm","今晚见",false)));assertEquals(ParseResult.Disposition.REVIEW,parser.parse(sample("com.tencent.mm","已支付¥27.60",true)).disposition);}
 @Test public void productionCatalogNeedsNoRoutingMetadata(){var catalog=TemplateCatalog.fromJson("{\"formatVersion\":1,\"templates\":[{\"id\":\"alipay\",\"version\":1,\"parserType\":\"ALIPAY_EXPENSE_REMINDER\",\"provider\":\"ALIPAY\",\"packageName\":\"com.eg.android.AlipayGphone\",\"bodyPrefix\":\"你有一笔\"}]}");assertEquals(1,catalog.size());}
}
