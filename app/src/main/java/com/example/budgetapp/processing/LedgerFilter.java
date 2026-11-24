package com.example.budgetapp.processing;

/** Shared immutable scope for statistics and the corresponding ledger rows. */
public final class LedgerFilter {
    public enum Metric { EXPENSE, INCOME, NET_OUTFLOW }
    public final long startMs;
    public final long endMs;
    public final Metric metric;
    public final String categoryId;
    public final String provider;
    public final String currency = "CNY";
    public LedgerFilter(long startMs, long endMs, Metric metric, String categoryId, String provider) {
        if (startMs > endMs || metric == null) throw new IllegalArgumentException("Invalid ledger scope");
        this.startMs = startMs;
        this.endMs = endMs;
        this.metric = metric;
        this.categoryId = categoryId;
        this.provider = provider;
    }
    public LedgerFilter withCategory(String categoryId) {
        return new LedgerFilter(startMs, endMs, metric, categoryId, provider);
    }
    public LedgerFilter withProvider(String provider) {
        return new LedgerFilter(startMs, endMs, metric, categoryId, provider);
    }
    public LedgerFilter withRange(long startMs, long endMs) {
        return new LedgerFilter(startMs, endMs, metric, categoryId, provider);
    }
}
