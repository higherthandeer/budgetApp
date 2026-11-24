package com.example.budgetapp.data;

import com.example.budgetapp.data.entity.*;
import com.example.budgetapp.processing.*;

import java.time.Clock;
import java.util.*;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Synchronous transactional operations; callers use the application IO executor.
 */
public final class ManualLedgerRepository {
    public static final class SaveResult {
        public enum Status {SAVED, DUPLICATE_WARNING, CONFLICT}

        public final Status status;
        public final String transactionId;
        public final long revision;
        public final List<String> similarIds;

        SaveResult(Status status, String id, long revision, List<String> similar) {
            this.status = status;
            transactionId = id;
            this.revision = revision;
            similarIds = Collections.unmodifiableList(new ArrayList<>(similar));
        }
    }

    public static final class WriteResult {
        public enum Status {APPLIED, STALE, NOT_ALLOWED}

        public final Status status;
        public final long revision;

        WriteResult(Status status, long revision) {
            this.status = status;
            this.revision = revision;
        }
    }

    private final LedgerDatabase db;
    private final Clock clock;

    public ManualLedgerRepository(LedgerDatabase db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public SaveResult save(String submissionId, TransactionDraft draft, boolean confirmDistinct) {
        if (submissionId == null || submissionId.trim().isEmpty())
            throw new IllegalArgumentException("Missing submission");
        return db.runInTransaction(() -> {
            SourceEventEntity prior = db.sourceEventDao().findByKey("MANUAL", submissionId);
            if (prior != null) {
                LedgerTransactionEntity old = db.ledgerDao().find(prior.transactionId);
                return new SaveResult(SaveResult.Status.SAVED, old.id, old.revision, Collections.emptyList());
            }
            if (draft.categoryExplicit)
                validateAssignment(draft.categoryId, draft.kind.name(), null);
            var candidate = new com.example.budgetapp.parsing.ParsedTransaction();
            candidate.kind = draft.kind;
            candidate.amountMinor = draft.amountMinor;
            candidate.occurredAtMs = draft.occurredAtMs;
            candidate.timePrecision = draft.timePrecision;
            candidate.merchant = draft.merchant;
            List<String> similar = TransactionProcessor.similarTransactions(db, candidate, draft.provider, draft.accountScopeId);
            if (!confirmDistinct && !similar.isEmpty())
                return new SaveResult(SaveResult.Status.DUPLICATE_WARNING, null, 0, similar);
            long now = clock.millis();
            LedgerTransactionEntity tx = new LedgerTransactionEntity();
            tx.id = UUID.randomUUID().toString();
            apply(tx, draft);
            tx.bookingState = "POSTED";
            tx.revision = 1;
            tx.createdAt = now;
            tx.updatedAt = now;
            tx.userLockedFields = initialLocks(draft);
            validateAssignment(tx.categoryId, tx.kind, null);
            db.ledgerDao().insert(tx);
            SourceEventEntity e = new SourceEventEntity();
            e.id = UUID.randomUUID().toString();
            e.sourceType = "MANUAL";
            e.provider = draft.provider;
            e.accountScopeId = draft.accountScopeId;
            e.capturedAtMs = now;
            e.eventKey = submissionId;
            e.processState = "DONE";
            e.transactionId = tx.id;
            e.matchMethod = "USER";
            e.linkedAt = now;
            e.parsedJson = facts(tx);
            if (db.sourceEventDao().insertIgnore(e) == -1)
                throw new IllegalStateException("Submission conflict");
            return new SaveResult(SaveResult.Status.SAVED, tx.id, tx.revision, similar);
        });
    }

    public WriteResult update(String id, long revision, TransactionDraft draft) {
        return db.runInTransaction(() -> {
            LedgerTransactionEntity tx = db.ledgerDao().find(id);
            WriteResult rejected = guard(tx, revision);
            if (rejected != null) return rejected;
            if (!"POSTED".equals(tx.bookingState))
                return new WriteResult(WriteResult.Status.NOT_ALLOWED, tx.revision);
            String before = facts(tx), oldCategory = tx.categoryId, oldOrigin = tx.categoryOrigin, oldRule = tx.appliedRuleId;
            boolean sameKind = tx.kind.equals(draft.kind.name());
            Set<String> locks = readLocks(tx.userLockedFields);
            boolean sameClassificationInputs = sameKind && normalize(tx.merchantRaw).equals(normalize(draft.merchant)) && tx.provider.equals(draft.provider) && tx.accountScopeId.equals(draft.accountScopeId);
            boolean keepCategory = !draft.categoryExplicit && sameKind && (sameClassificationInputs || "USER".equals(oldOrigin) || locks.contains("category"));
            if (draft.categoryExplicit)
                validateAssignment(draft.categoryId, draft.kind.name(), sameKind ? oldCategory : null);
            if (tx.amountMinor != draft.amountMinor) locks.add("amount");
            if (tx.occurredAtMs != draft.occurredAtMs || !tx.timePrecision.equals(draft.timePrecision.name()))
                locks.add("date");
            if (!sameKind) {
                locks.add("kind");
                if (!draft.categoryExplicit) locks.remove("category");
            }
            if (!tx.merchantRaw.equals(draft.merchant)) locks.add("merchant");
            if (!tx.note.equals(draft.note)) locks.add("note");
            if (draft.categoryExplicit) locks.add("category");
            apply(tx, draft);
            if (keepCategory) {
                tx.categoryId = oldCategory;
                tx.categoryOrigin = oldOrigin;
                tx.appliedRuleId = oldRule;
            }
            validateAssignment(tx.categoryId, tx.kind, sameKind ? oldCategory : null);
            tx.recentPreviousValuesJson = before;
            tx.userLockedFields = new JSONArray(locks).toString();
            return persist(tx);
        });
    }

    public WriteResult delete(String id, long revision) {
        return changeDeleted(id, revision, false);
    }

    public WriteResult undoDelete(String id, long revision) {
        return changeDeleted(id, revision, true);
    }

    private WriteResult changeDeleted(String id, long revision, boolean undo) {
        return db.runInTransaction(() -> {
            LedgerTransactionEntity tx = db.ledgerDao().find(id);
            WriteResult rejected = guard(tx, revision);
            if (rejected != null) return rejected;
            if (undo ? (!"DELETED".equals(tx.bookingState) || !"USER_DELETE".equals(tx.deletionReason)) : !"POSTED".equals(tx.bookingState))
                return new WriteResult(WriteResult.Status.NOT_ALLOWED, tx.revision);
            tx.bookingState = undo ? "POSTED" : "DELETED";
            tx.deletionReason = undo ? null : "USER_DELETE";
            tx.deletedAt = undo ? null : clock.millis();
            return persist(tx);
        });
    }

    public List<LedgerTransactionEntity> list(LedgerFilter f) {
        return db.ledgerDao().listPosted(f.startMs, f.endMs, f.metric == LedgerFilter.Metric.NET_OUTFLOW ? null : f.metric.name(), f.categoryId, f.provider);
    }

    private WriteResult guard(LedgerTransactionEntity tx, long revision) {
        if (tx == null) return new WriteResult(WriteResult.Status.NOT_ALLOWED, 0);
        return tx.revision != revision ? new WriteResult(WriteResult.Status.STALE, tx.revision) : null;
    }

    private WriteResult persist(LedgerTransactionEntity tx) {
        tx.revision = Math.incrementExact(tx.revision);
        tx.updatedAt = clock.millis();
        db.ledgerDao().update(tx);
        return new WriteResult(WriteResult.Status.APPLIED, tx.revision);
    }

    private void validateAssignment(String id, String kind, String historicalCategory) {
        CategoryEntity c = db.categoryDao().find(id);
        if (c == null || !c.flowScope.equals(kind) || c.archived && !id.equals(historicalCategory))
            throw new IllegalArgumentException("Category is unavailable for this assignment");
    }

    private String initialLocks(TransactionDraft draft) {
        Set<String> locks = new LinkedHashSet<>(Arrays.asList("amount", "date", "kind", "merchant"));
        if (draft.categoryExplicit) locks.add("category");
        return new JSONArray(locks).toString();
    }

    private Set<String> readLocks(String text) {
        Set<String> locks = new LinkedHashSet<>();
        try {
            JSONArray array = new JSONArray(text);
            for (int i = 0; i < array.length(); i++) locks.add(array.getString(i));
        } catch (JSONException invalid) {
            locks.addAll(Arrays.asList("amount", "date", "kind", "merchant", "category"));
        }
        return locks;
    }

    private static String normalize(String merchant) {
        return merchant.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private void apply(LedgerTransactionEntity tx, TransactionDraft d) {
        tx.kind = d.kind.name();
        tx.amountMinor = d.amountMinor;
        tx.currency = "CNY";
        tx.occurredAtMs = d.occurredAtMs;
        tx.timePrecision = d.timePrecision.name();
        tx.sourceZoneId = "Asia/Shanghai";
        tx.merchantRaw = d.merchant;
        tx.merchantNormalized = normalize(d.merchant);
        tx.provider = d.provider;
        tx.accountScopeId = d.accountScopeId;
        if (d.categoryExplicit) {
            tx.categoryId = d.categoryId;
            tx.categoryOrigin = "USER";
            tx.appliedRuleId = null;
        } else {
            var match = new CategoryMatcher().match(d.kind, d.merchant, d.provider, d.accountScopeId, db.categoryDao().ruleRows());
            tx.categoryId = match.categoryId;
            tx.categoryOrigin = match.origin.name();
            tx.appliedRuleId = match.ruleId;
        }
        tx.note = d.note;
    }

    private String facts(LedgerTransactionEntity tx) {
        try {
            return new JSONObject().put("kind", tx.kind).put("amountMinor", tx.amountMinor).put("currency", tx.currency).put("occurredAtMs", tx.occurredAtMs).put("timePrecision", tx.timePrecision).put("merchant", tx.merchantRaw).put("provider", tx.provider).put("accountScopeId", tx.accountScopeId).put("categoryId", tx.categoryId).put("note", tx.note).toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
