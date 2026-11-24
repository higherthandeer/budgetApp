package com.example.budgetapp.processing;

import com.example.budgetapp.processing.LedgerTypes.Kind;
import com.example.budgetapp.processing.LedgerTypes.TimePrecision;

/** Validated user input; source observations are stored separately. */
public final class TransactionDraft {
    public final Kind kind;
    public final long amountMinor;
    public final long occurredAtMs;
    public final TimePrecision timePrecision;
    public final String merchant;
    public final String provider;
    public final String accountScopeId;
    public final String categoryId;
    public final boolean categoryExplicit;
    public final String note;
    public final String currency = "CNY";
    public TransactionDraft(Kind kind, long amountMinor, long occurredAtMs, TimePrecision timePrecision,
            String merchant, String provider, String accountScopeId, String categoryId, String note) {
        this(kind,amountMinor,occurredAtMs,timePrecision,merchant,provider,accountScopeId,categoryId,note,categoryId!=null);
    }
    public TransactionDraft(Kind kind, long amountMinor, long occurredAtMs, TimePrecision timePrecision,
            String merchant, String provider, String accountScopeId, String categoryId, String note,
            boolean categoryExplicit) {
        if (kind == null || amountMinor <= 0 || timePrecision == null
                || merchant == null || note == null || provider == null || provider.trim().isEmpty()
                || accountScopeId == null || accountScopeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Incomplete transaction draft");
        }
        this.kind = kind;
        this.amountMinor = amountMinor;
        this.occurredAtMs = occurredAtMs;
        this.timePrecision = timePrecision;
        this.merchant = merchant;
        this.provider = provider;
        this.accountScopeId = accountScopeId;
        this.categoryExplicit = categoryExplicit;
        this.categoryId = categoryId == null
                ? (kind == Kind.EXPENSE ? LedgerTypes.UNCATEGORIZED_EXPENSE : LedgerTypes.UNCATEGORIZED_INCOME)
                : categoryId;
        this.note = note;
    }
}
