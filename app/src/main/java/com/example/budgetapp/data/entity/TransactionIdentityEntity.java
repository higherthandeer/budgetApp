package com.example.budgetapp.data.entity;

import androidx.annotation.NonNull;
import androidx.room.*;

@Entity(tableName = "transaction_identity",
        foreignKeys = {
                @ForeignKey(entity = LedgerTransactionEntity.class,
                        parentColumns = "id", childColumns = "transaction_id",
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT)
        },
        indices = {
                @Index(value = {"transaction_id"}, unique = false),
                @Index(value = {"provider", "account_scope_id", "id_kind", "id_namespace", "external_id"}, unique = true)
        }
)
public class TransactionIdentityEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = "";
    @NonNull
    @ColumnInfo(name = "transaction_id")
    public String transactionId = "";
    @NonNull
    @ColumnInfo(name = "provider")
    public String provider = "";
    @NonNull
    @ColumnInfo(name = "account_scope_id")
    public String accountScopeId = "";
    @NonNull
    @ColumnInfo(name = "id_kind")
    public String idKind = "";
    @NonNull
    @ColumnInfo(name = "id_namespace")
    public String idNamespace = "";
    @NonNull
    @ColumnInfo(name = "external_id")
    public String externalId = "";
}
