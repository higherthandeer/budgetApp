package com.example.budgetapp.data.entity;

import androidx.annotation.NonNull;
import androidx.room.*;

@Entity(tableName="ledger_transaction",foreignKeys={@ForeignKey(entity=CategoryEntity.class,parentColumns="id",childColumns="category_id",onDelete=ForeignKey.RESTRICT,onUpdate=ForeignKey.RESTRICT),@ForeignKey(entity=CategoryRuleEntity.class,parentColumns="id",childColumns="applied_rule_id",onDelete=ForeignKey.RESTRICT,onUpdate=ForeignKey.RESTRICT),@ForeignKey(entity=LedgerTransactionEntity.class,parentColumns="id",childColumns="merged_into_id",onDelete=ForeignKey.RESTRICT,onUpdate=ForeignKey.RESTRICT)},indices={@Index(value={"category_id"},unique=false),@Index(value={"applied_rule_id"},unique=false),@Index(value={"merged_into_id"},unique=false),@Index(value={"booking_state","currency","occurred_at_ms"},unique=false),@Index(value={"category_id","occurred_at_ms"},unique=false)})
public class LedgerTransactionEntity {
    @PrimaryKey @NonNull @ColumnInfo(name="id") public String id = "";
    @NonNull @ColumnInfo(name="kind") public String kind = "";
    @ColumnInfo(name="amount_minor") public long amountMinor;
    @NonNull @ColumnInfo(name="currency") public String currency = "";
    @ColumnInfo(name="occurred_at_ms") public long occurredAtMs;
    @NonNull @ColumnInfo(name="time_precision") public String timePrecision = "";
    @NonNull @ColumnInfo(name="source_zone_id") public String sourceZoneId = "";
    @NonNull @ColumnInfo(name="merchant_raw") public String merchantRaw = "";
    @NonNull @ColumnInfo(name="merchant_normalized") public String merchantNormalized = "";
    @NonNull @ColumnInfo(name="provider") public String provider = "";
    @NonNull @ColumnInfo(name="account_scope_id") public String accountScopeId = "";
    @ColumnInfo(name="pay_method_raw") public String payMethodRaw;
    @NonNull @ColumnInfo(name="category_id") public String categoryId = "";
    @NonNull @ColumnInfo(name="category_origin") public String categoryOrigin = "";
    @ColumnInfo(name="applied_rule_id") public String appliedRuleId;
    @NonNull @ColumnInfo(name="booking_state") public String bookingState = "";
    @ColumnInfo(name="deleted_at") public Long deletedAt;
    @NonNull @ColumnInfo(name="user_locked_fields") public String userLockedFields = "";
    @NonNull @ColumnInfo(name="note") public String note = "";
    @ColumnInfo(name="revision") public long revision;
    @ColumnInfo(name="created_at") public long createdAt;
    @ColumnInfo(name="updated_at") public long updatedAt;
    @ColumnInfo(name="deletion_reason") public String deletionReason;
    @ColumnInfo(name="merged_into_id") public String mergedIntoId;
    @ColumnInfo(name="recent_previous_values_json") public String recentPreviousValuesJson;
}
