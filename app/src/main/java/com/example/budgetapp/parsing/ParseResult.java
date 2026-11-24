package com.example.budgetapp.parsing;

public final class ParseResult {
    public enum Disposition {CANDIDATE, REVIEW, IGNORED}

    public final Disposition disposition;
    public final ParsedTransaction candidate;
    public final String reasonCode, parserId;
    public final int version;

    public ParseResult(Disposition disposition, ParsedTransaction candidate, String reasonCode, String parserId, int version) {
        this.disposition = disposition;
        this.candidate = candidate;
        this.reasonCode = reasonCode;
        this.parserId = parserId;
        this.version = version;
    }
}
