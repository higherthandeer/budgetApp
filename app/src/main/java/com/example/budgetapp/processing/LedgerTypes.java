package com.example.budgetapp.processing;

public final class LedgerTypes {
    private LedgerTypes() {}
    public enum Kind { EXPENSE, INCOME }
    public enum BookingState { POSTED, DELETED }
    public enum SourceType { NOTIFICATION, MANUAL }
    public enum ProcessState { NEW, DONE, REVIEW, IGNORED, ERROR }
    public enum TimePrecision { DAY, MINUTE, SECOND }
    public enum CategoryOrigin { USER, RULE, DEFAULT }
    public static final String UNCATEGORIZED_EXPENSE = "00000000-0000-0000-0000-000000000001";
    public static final String UNCATEGORIZED_INCOME = "00000000-0000-0000-0000-000000000002";
}
