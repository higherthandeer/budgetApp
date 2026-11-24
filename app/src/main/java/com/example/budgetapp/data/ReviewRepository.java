package com.example.budgetapp.data;

import com.example.budgetapp.data.entity.*;
import com.example.budgetapp.data.ManualLedgerRepository.WriteResult;
import com.example.budgetapp.parsing.*;
import com.example.budgetapp.parsing.ParsedTransaction.*;
import com.example.budgetapp.processing.*;

import java.time.Clock;
import java.util.*;

public final class ReviewRepository {
    private final LedgerDatabase db;
    private final Clock clock;
    private final ManualLedgerRepository manual;

    public ReviewRepository(LedgerDatabase db, Clock clock) {
        this.db = db;
        this.clock = clock;
        manual = new ManualLedgerRepository(db, clock);
    }

    public List<SourceEventEntity> listOpen() {
        return db.sourceEventDao().listReview();
    }

    public WriteResult confirmNew(String eventId, TransactionDraft corrected) {
        return db.runInTransaction(() -> {
            SourceEventEntity e = db.sourceEventDao().find(eventId);
            WriteResult resolved = resolved(e);
            if (resolved != null) return resolved;
            if (!corrected.provider.equals(e.provider) || !corrected.accountScopeId.equals(e.accountScopeId) || !identitiesAvailable(e, null))
                return denied();
            var saved = manual.save("review." + eventId, corrected, true);
            LedgerTransactionEntity tx = db.ledgerDao().find(saved.transactionId);
            attachIdentities(e, tx.id);
            finish(e, tx.id, "CONFIRM_NEW", corrected);
            return applied(tx.revision);
        });
    }

    public WriteResult linkExisting(String eventId, String txId) {
        return db.runInTransaction(() -> {
            SourceEventEntity e = db.sourceEventDao().find(eventId);
            WriteResult resolved = resolved(e);
            if (resolved != null) return resolved;
            LedgerTransactionEntity tx = db.ledgerDao().find(txId);
            if (tx == null || !e.provider.equals(tx.provider) || !e.accountScopeId.equals(tx.accountScopeId) || !identitiesAvailable(e, txId))
                return denied();
            attachIdentities(e, txId);
            finish(e, txId, "LINK_EXISTING", null);
            return applied(tx.revision);
        });
    }

    public WriteResult acceptCorrection(String eventId, String txId, long revision, TransactionDraft corrected) {
        return db.runInTransaction(() -> {
            SourceEventEntity e = db.sourceEventDao().find(eventId);
            WriteResult resolved = resolved(e);
            if (resolved != null) return resolved;
            if (!identitiesAvailable(e, txId) || !corrected.provider.equals(e.provider) || !corrected.accountScopeId.equals(e.accountScopeId))
                return denied();
            WriteResult result = manual.update(txId, revision, corrected);
            if (result.status != WriteResult.Status.APPLIED) return result;
            attachIdentities(e, txId);
            finish(e, txId, "ACCEPT_CORRECTION", corrected);
            return result;
        });
    }

    public WriteResult ignore(String eventId) {
        return db.runInTransaction(() -> {
            SourceEventEntity e = db.sourceEventDao().find(eventId);
            if (e == null) return denied();
            if ("IGNORED".equals(e.processState)) return applied(0);
            if (!"REVIEW".equals(e.processState)) return denied();
            e.processState = "IGNORED";
            e.reviewResolution = "USER_IGNORE";
            e.resolvedAt = clock.millis();
            db.sourceEventDao().update(e);
            return applied(0);
        });
    }

    public WriteResult merge(String primaryId, long primaryRevision, String duplicateId, long duplicateRevision) {
        return db.runInTransaction(() -> {
            if (primaryId.equals(duplicateId)) return denied();
            LedgerTransactionEntity primary = db.ledgerDao().find(primaryId), duplicate = db.ledgerDao().find(duplicateId);
            if (primary == null || duplicate == null) return denied();
            if (primary.revision != primaryRevision || duplicate.revision != duplicateRevision)
                return new WriteResult(WriteResult.Status.STALE, 0);
            if (!"POSTED".equals(primary.bookingState) || !"POSTED".equals(duplicate.bookingState) || !primary.kind.equals(duplicate.kind) || !primary.currency.equals(duplicate.currency) || primary.mergedIntoId != null || duplicate.mergedIntoId != null)
                return denied();
            for (var a : db.ledgerDao().identities(primaryId))
                for (var b : db.ledgerDao().identities(duplicateId))
                    if (a.provider.equals(b.provider) && a.accountScopeId.equals(b.accountScopeId) && a.idKind.equals(b.idKind) && a.idNamespace.equals(b.idNamespace) && !a.externalId.equals(b.externalId))
                        return denied();
            db.sourceEventDao().transferEvidence(primaryId, duplicateId);
            db.ledgerDao().transferIdentities(primaryId, duplicateId);
            duplicate.bookingState = "DELETED";
            duplicate.deletionReason = "MERGE";
            duplicate.deletedAt = clock.millis();
            duplicate.mergedIntoId = primaryId;
            duplicate.revision++;
            duplicate.updatedAt = clock.millis();
            db.ledgerDao().update(duplicate);
            primary.revision++;
            primary.updatedAt = clock.millis();
            db.ledgerDao().update(primary);
            return applied(primary.revision);
        });
    }

    private WriteResult resolved(SourceEventEntity e) {
        if (e == null) return denied();
        if ("DONE".equals(e.processState)) {
            LedgerTransactionEntity tx = e.transactionId == null ? null : db.ledgerDao().find(e.transactionId);
            return tx == null ? denied() : applied(tx.revision);
        }
        return "REVIEW".equals(e.processState) ? null : denied();
    }

    private ParsedTransaction observed(SourceEventEntity e) {
        String json = e.parsedJson != null ? e.parsedJson : e.candidateJson;
        if (json == null) return null;
        try {
            return EventCodec.decodeParsed(json).candidate;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private boolean identitiesAvailable(SourceEventEntity e, String allowedTx) {
        ParsedTransaction p = observed(e);
        if (p == null) return true;
        for (var id : p.identities) {
            if (id.idKind == null || id.idKind.isEmpty() || id.namespace == null || id.namespace.isEmpty() || id.externalId == null || id.externalId.isEmpty())
                return false;
            var old = db.ledgerDao().identity(e.provider, e.accountScopeId, id.idKind, id.namespace, id.externalId);
            if (old != null && !old.transactionId.equals(allowedTx)) return false;
        }
        return true;
    }

    private void attachIdentities(SourceEventEntity e, String txId) {
        ParsedTransaction p = observed(e);
        if (p == null) return;
        for (var id : p.identities) {
            if (db.ledgerDao().identity(e.provider, e.accountScopeId, id.idKind, id.namespace, id.externalId) != null)
                continue;
            TransactionIdentityEntity row = new TransactionIdentityEntity();
            row.id = UUID.randomUUID().toString();
            row.transactionId = txId;
            row.provider = e.provider;
            row.accountScopeId = e.accountScopeId;
            row.idKind = id.idKind;
            row.idNamespace = id.namespace;
            row.externalId = id.externalId;
            db.ledgerDao().insertIdentity(row);
        }
    }

    private void finish(SourceEventEntity e, String txId, String resolution, TransactionDraft corrected) {
        if (e.parsedJson == null && e.candidateJson != null) e.parsedJson = e.candidateJson;
        if (corrected != null) {
            ParsedTransaction p = new ParsedTransaction();
            p.kind = corrected.kind;
            p.amountMinor = corrected.amountMinor;
            p.currency = "CNY";
            p.occurredAtMs = corrected.occurredAtMs;
            p.timePrecision = corrected.timePrecision;
            p.merchant = corrected.merchant;
            p.tradeStatus = TradeStatus.SUCCESS;
            p.proofs.addAll(EnumSet.allOf(Proof.class));
            e.correctedCandidateJson = EventCodec.encodeParsed(new ParseResult(ParseResult.Disposition.CANDIDATE, p, "USER_CONFIRMED", null, 0));
        }
        e.transactionId = txId;
        e.matchMethod = "USER";
        e.linkedAt = clock.millis();
        e.processState = "DONE";
        e.reviewResolution = resolution;
        e.resolvedAt = clock.millis();
        db.sourceEventDao().update(e);
    }

    private WriteResult denied() {
        return new WriteResult(WriteResult.Status.NOT_ALLOWED, 0);
    }

    private WriteResult applied(long revision) {
        return new WriteResult(WriteResult.Status.APPLIED, revision);
    }
}
