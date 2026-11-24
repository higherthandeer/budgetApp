package com.example.budgetapp.processing;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/** All boundaries are half open and independent of the device time zone. */
public final class TimeWindows {
    public static final ZoneId LEDGER_ZONE = ZoneId.of("Asia/Shanghai");
    private TimeWindows() {}
    public static final class Range {
        public final long startMs;
        public final long endMs;
        public Range(long startMs, long endMs) {
            if (startMs > endMs) throw new IllegalArgumentException("Reversed range");
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }
    public static Range month(YearMonth month) {
        return new Range(dayAnchor(month.atDay(1)), dayAnchor(month.plusMonths(1).atDay(1)));
    }
    public static Range inclusiveDates(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) throw new IllegalArgumentException("Reversed dates");
        return new Range(dayAnchor(start), dayAnchor(end.plusDays(1)));
    }
    public static long dayAnchor(LocalDate date) {
        return date.atStartOfDay(LEDGER_ZONE).toInstant().toEpochMilli();
    }
}
