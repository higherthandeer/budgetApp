package com.example.budgetapp.parsing;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class NotificationGoldenTest {
 @Test public void onlyUserRequestedWechatAndAlipayFormatsHaveProductionTemplates(){assertEquals(2,TemplateCatalog.load(ApplicationProvider.getApplicationContext()).size());}
}
