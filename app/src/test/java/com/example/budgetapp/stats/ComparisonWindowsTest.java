package com.example.budgetapp.stats;
import java.time.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ComparisonWindowsTest {
 @Test public void currentMonthUsesOnlyComparableCompleteLedgerDays(){var p=ComparisonWindows.monthToDate(Clock.fixed(Instant.parse("2026-11-06T04:00:00Z"),ZoneId.of("America/Los_Angeles"))).get();assertEquals(5,p.comparedDays);assertEquals(Instant.parse("2026-11-05T16:00:00Z").toEpochMilli(),p.current.endMs);assertEquals(Instant.parse("2026-10-05T16:00:00Z").toEpochMilli(),p.previous.endMs);assertFalse(ComparisonWindows.monthToDate(Clock.fixed(Instant.parse("2026-10-31T16:00:00Z"),ZoneOffset.UTC)).isPresent());}
 @Test public void shortMonthsLeapYearsAndYearBoundaryAreExplicit(){var normal=ComparisonWindows.monthToDate(Clock.fixed(Instant.parse("2025-03-31T04:00:00Z"),ZoneOffset.UTC)).get();assertEquals(28,normal.comparedDays);assertEquals(Instant.parse("2025-03-28T16:00:00Z").toEpochMilli(),normal.current.endMs);assertEquals(29,ComparisonWindows.monthToDate(Clock.fixed(Instant.parse("2024-03-31T04:00:00Z"),ZoneOffset.UTC)).get().comparedDays);var year=ComparisonWindows.completedMonth(YearMonth.of(2026,1));assertEquals(Instant.parse("2025-11-30T16:00:00Z").toEpochMilli(),year.previous.startMs);assertEquals(Instant.parse("2026-01-31T16:00:00Z").toEpochMilli(),year.current.endMs);}
}
