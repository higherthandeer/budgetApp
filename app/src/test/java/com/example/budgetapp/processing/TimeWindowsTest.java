package com.example.budgetapp.processing;

import org.junit.Test;
import java.time.*;
import java.util.TimeZone;
import static org.junit.Assert.*;
import static com.example.budgetapp.processing.LedgerTypes.*;

public class TimeWindowsTest {
    @Test public void monthIsHalfOpenInLedgerZone() {
        TimeWindows.Range r = TimeWindows.month(YearMonth.of(2026,11));
        assertEquals(Instant.parse("2026-10-31T16:00:00Z").toEpochMilli(), r.startMs);
        assertEquals(Instant.parse("2026-11-30T16:00:00Z").toEpochMilli(), r.endMs);
    }
    @Test public void dayPrecisionUsesLedgerZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            assertEquals(Instant.parse("2026-11-04T16:00:00Z").toEpochMilli(), TimeWindows.dayAnchor(LocalDate.of(2026,11,5)));
            assertEquals(Instant.parse("2026-10-31T16:00:00Z").toEpochMilli(), TimeWindows.month(YearMonth.of(2026,11)).startMs);
        } finally { TimeZone.setDefault(original); }
    }
    @Test public void inclusiveDatesCoverLeapDayAndYearBoundary() {
        TimeWindows.Range r = TimeWindows.inclusiveDates(LocalDate.of(2024,2,28),LocalDate.of(2024,2,29));
        assertEquals(Instant.parse("2024-02-27T16:00:00Z").toEpochMilli(),r.startMs);
        assertEquals(Instant.parse("2024-02-29T16:00:00Z").toEpochMilli(),r.endMs);
        assertEquals(Instant.parse("2027-01-01T16:00:00Z").toEpochMilli(),TimeWindows.inclusiveDates(LocalDate.of(2026,12,31),LocalDate.of(2027,1,1)).endMs);
        assertThrows(IllegalArgumentException.class,()->TimeWindows.inclusiveDates(LocalDate.of(2026,11,6),LocalDate.of(2026,11,5)));
    }
    @Test public void filterPreservesScopeAndAllowsEmptyRange() {
        LedgerFilter f = new LedgerFilter(10,20,LedgerFilter.Metric.NET_OUTFLOW,"food","WECHAT");
        LedgerFilter next = f.withCategory("travel").withProvider("ALIPAY").withRange(30,30);
        assertEquals(10,f.startMs); assertEquals("food",f.categoryId);
        assertEquals(30,next.endMs); assertEquals("travel",next.categoryId);
        assertEquals("ALIPAY",next.provider); assertEquals(LedgerFilter.Metric.NET_OUTFLOW,next.metric);
        assertThrows(IllegalArgumentException.class,()->f.withRange(21,20));
        assertThrows(IllegalArgumentException.class,()->new LedgerFilter(0,1,null,null,null));
    }
    @Test public void draftValidatesRequiredFactsAndDefaultsCategoryByKind() {
        TransactionDraft expense = draft(Kind.EXPENSE,1,TimePrecision.DAY,"WECHAT","scope");
        assertEquals(UNCATEGORIZED_EXPENSE,expense.categoryId); assertEquals("CNY",expense.currency);
        assertEquals(UNCATEGORIZED_INCOME,draft(Kind.INCOME,1,TimePrecision.SECOND,"OTHER","scope").categoryId);
        assertThrows(IllegalArgumentException.class,()->draft(null,1,TimePrecision.DAY,"OTHER","scope"));
        assertThrows(IllegalArgumentException.class,()->draft(Kind.EXPENSE,0,TimePrecision.DAY,"OTHER","scope"));
        assertThrows(IllegalArgumentException.class,()->draft(Kind.EXPENSE,1,null,"OTHER","scope"));
        assertThrows(IllegalArgumentException.class,()->draft(Kind.EXPENSE,1,TimePrecision.DAY,"","scope"));
        assertThrows(IllegalArgumentException.class,()->draft(Kind.EXPENSE,1,TimePrecision.DAY,"OTHER",""));
    }
    private TransactionDraft draft(Kind kind,long amount,TimePrecision precision,String provider,String scope) {
        return new TransactionDraft(kind,amount,1793808000000L,precision,"",provider,scope,null,"");
    }
}
