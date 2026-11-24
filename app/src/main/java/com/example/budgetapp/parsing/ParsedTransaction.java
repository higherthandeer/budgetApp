package com.example.budgetapp.parsing;

import com.example.budgetapp.processing.LedgerTypes.*;

import java.util.*;

public final class ParsedTransaction {
    public enum TradeStatus {SUCCESS, PROCESSING, FAILED, CLOSED, UNKNOWN}

    public enum Proof {AMOUNT, CURRENCY, KIND, STATUS, DATE, ORIGIN}

    public enum DateBasis {TRANSACTION_TEXT, NOTIFICATION_RECEIVED_DAY, NOTIFICATION_POST_TIME, NOTIFICATION_CAPTURE_TIME}

    public static final class Identity {
        public final String idKind, namespace, externalId;

        public Identity(String idKind, String namespace, String externalId) {
            this.idKind = idKind;
            this.namespace = namespace;
            this.externalId = externalId;
        }
    }

    public Kind kind;
    public Long amountMinor, occurredAtMs;
    public String currency, merchant, payMethod;
    public TimePrecision timePrecision;
    public TradeStatus tradeStatus = TradeStatus.UNKNOWN;
    public DateBasis dateBasis = DateBasis.TRANSACTION_TEXT;
    public final Set<Proof> proofs = EnumSet.noneOf(Proof.class);
    public final List<Identity> identities = new ArrayList<>();
}
