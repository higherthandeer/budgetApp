package com.example.budgetapp.data.dao;

import androidx.room.*;

import java.util.List;

import com.example.budgetapp.data.entity.*;

@Dao
public interface LedgerDao {
    @Query("SELECT * FROM ledger_transaction WHERE provider=:provider AND account_scope_id=:scope")
    List<LedgerTransactionEntity> inScope(String provider, String scope);

    @Insert
    void insert(LedgerTransactionEntity transaction);

    @Update
    int update(LedgerTransactionEntity transaction);

    @Query("SELECT * FROM ledger_transaction WHERE id=:id")
    LedgerTransactionEntity find(String id);

    @Query("SELECT COUNT(*) FROM ledger_transaction WHERE booking_state='POSTED' AND currency='CNY'")
    int countPosted();

    @Query("SELECT * FROM ledger_transaction WHERE booking_state='POSTED' AND currency='CNY' AND occurred_at_ms>=:startMs AND occurred_at_ms<:endMs AND (:kind IS NULL OR kind=:kind) AND (:categoryId IS NULL OR category_id=:categoryId) AND (:provider IS NULL OR provider=:provider) ORDER BY occurred_at_ms DESC,created_at DESC,id")
    List<LedgerTransactionEntity> listPosted(long startMs, long endMs, String kind, String categoryId, String provider);

    @Query("UPDATE transaction_identity SET transaction_id=:primaryId WHERE transaction_id=:duplicateId")
    int transferIdentities(String primaryId, String duplicateId);

    @Insert
    void insertIdentity(TransactionIdentityEntity identity);

    @Query("SELECT * FROM transaction_identity WHERE provider=:provider AND account_scope_id=:scope AND id_kind=:kind AND id_namespace=:namespace AND external_id=:externalId")
    TransactionIdentityEntity identity(String provider, String scope, String kind, String namespace, String externalId);

    @Query("SELECT * FROM transaction_identity WHERE transaction_id=:id")
    List<TransactionIdentityEntity> identities(String id);
}
