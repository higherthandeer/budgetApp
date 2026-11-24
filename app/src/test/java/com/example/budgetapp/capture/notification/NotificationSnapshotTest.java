package com.example.budgetapp.capture.notification;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class NotificationSnapshotTest {
    @Test public void missingTextIsNormalizedWithoutHoldingMutableInput() {
        List<String> lines = new ArrayList<>(Arrays.asList("one",null));
        NotificationSnapshot snapshot = sample(null,null,null,lines);
        lines.set(0,"changed");
        assertEquals("",snapshot.title);
        assertEquals("",snapshot.text);
        assertEquals("",snapshot.bigText);
        assertEquals("one",snapshot.textLines.get(0));
        assertEquals("",snapshot.textLines.get(1));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.textLines.clear());
    }

    @Test public void totalContentBudgetIncludesAllFieldsAndLines() {
        String longText = String.join("",Collections.nCopies(10000,"a"));
        NotificationSnapshot snapshot = sample(longText,longText,longText,Arrays.asList(longText));
        int total = snapshot.title.length()+snapshot.text.length()+snapshot.bigText.length();
        for (String line : snapshot.textLines) total += line.length();
        assertEquals(16384,total);
        assertTrue(snapshot.truncated);
    }

    @Test public void atMostThirtyTwoLinesAreRetained() {
        NotificationSnapshot snapshot = sample("","","",Collections.nCopies(33,"row"));
        assertEquals(32,snapshot.textLines.size());
        assertTrue(snapshot.truncated);
    }

    @Test public void ordinarySnapshotPreservesTimeAndSummaryAsEvidenceOnly() {
        NotificationSnapshot snapshot = new NotificationSnapshot("com.tencent.mm","key",10L,20L,
                "payments","title","28.80","",null,true,true,"8.0");
        assertEquals(10L,snapshot.postTimeMs);
        assertEquals(20L,snapshot.capturedAtMs);
        assertTrue(snapshot.groupSummary);
        assertTrue(snapshot.truncated);
        assertTrue(snapshot.textLines.isEmpty());
    }

    private static NotificationSnapshot sample(String title,String text,String big,List<String> lines) {
        return new NotificationSnapshot("com.tencent.mm","key",1L,2L,"channel",
                title,text,big,lines,false,false,"8.0");
    }
}
