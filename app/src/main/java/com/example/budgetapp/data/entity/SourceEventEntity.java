package com.example.budgetapp.data.entity;

import androidx.annotation.NonNull;
import androidx.room.*;

@Entity(tableName = "source_event",
        foreignKeys = {
                @ForeignKey(entity = LedgerTransactionEntity.class,
                        parentColumns = "id", childColumns = "transaction_id",
                        onDelete = ForeignKey.RESTRICT,
                        onUpdate = ForeignKey.RESTRICT)
        },
        indices = {
                @Index(value = {"transaction_id"}, unique = false),
                @Index(value = {"source_type", "event_key"}, unique = true),
                @Index(value = {"process_state", "captured_at_ms"},
                        unique = false)
        }
)
public class SourceEventEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = "";
    @NonNull
    @ColumnInfo(name = "source_type")
    public String sourceType = "";
    @NonNull
    @ColumnInfo(name = "provider")
    public String provider = "";
    @NonNull
    @ColumnInfo(name = "account_scope_id")
    public String accountScopeId = "";
    @ColumnInfo(name = "package_name")
    public String packageName;
    @ColumnInfo(name = "app_version")
    public String appVersion;
    @ColumnInfo(name = "captured_at_ms")
    public long capturedAtMs;
    @ColumnInfo(name = "source_time_raw")
    public String sourceTimeRaw;
    @ColumnInfo(name = "raw_payload")
    public String rawPayload;
    @ColumnInfo(name = "raw_expires_at_ms")
    public Long rawExpiresAtMs;
    @ColumnInfo(name = "payload_hash")
    public String payloadHash;
    @NonNull
    @ColumnInfo(name = "event_key")
    public String eventKey = "";
    @ColumnInfo(name = "parser_id")
    public String parserId;
    @ColumnInfo(name = "parser_version")
    public String parserVersion;
    @ColumnInfo(name = "parsed_json")
    public String parsedJson;
    @ColumnInfo(name = "candidate_json")
    public String candidateJson;
    @ColumnInfo(name = "corrected_candidate_json")
    public String correctedCandidateJson;
    @NonNull
    @ColumnInfo(name = "process_state")
    public String processState = "";
    @ColumnInfo(name = "error_code")
    public String errorCode;
    @ColumnInfo(name = "transaction_id")
    public String transactionId;
    @ColumnInfo(name = "match_method")
    public String matchMethod;
    @ColumnInfo(name = "linked_at")
    public Long linkedAt;
    @ColumnInfo(name = "review_reason_codes_json")
    public String reviewReasonCodesJson;
    @ColumnInfo(name = "review_candidates_json")
    public String reviewCandidatesJson;
    @ColumnInfo(name = "review_resolution")
    public String reviewResolution;
    @ColumnInfo(name = "resolved_at")
    public Long resolvedAt;
}
