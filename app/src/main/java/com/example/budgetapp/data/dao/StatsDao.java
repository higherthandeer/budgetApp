package com.example.budgetapp.data.dao;

import androidx.room.*;

import java.util.List;

@Dao
public interface StatsDao {
    class TotalsRow {
        public long expenseFen, incomeFen;
    }

    class CategoryRow {
        public String categoryId, name;
        public long amountFen;
    }

    @Query("SELECT COALESCE(SUM(CASE WHEN kind='EXPENSE' THEN amount_minor ELSE 0 END),0) AS expenseFen,COALESCE(SUM(CASE WHEN kind='INCOME' THEN amount_minor ELSE 0 END),0) AS incomeFen FROM ledger_transaction WHERE booking_state='POSTED' AND currency='CNY' AND occurred_at_ms>=:startMs AND occurred_at_ms<:endMs AND (:categoryId IS NULL OR category_id=:categoryId) AND (:provider IS NULL OR provider=:provider)")
    TotalsRow totals(long startMs, long endMs, String categoryId, String provider);

    @Query("SELECT t.category_id AS categoryId,c.name,SUM(t.amount_minor) AS amountFen FROM ledger_transaction t JOIN category c ON c.id=t.category_id WHERE t.booking_state='POSTED' AND t.currency='CNY' AND t.kind='EXPENSE' AND t.occurred_at_ms>=:startMs AND t.occurred_at_ms<:endMs AND (:categoryId IS NULL OR t.category_id=:categoryId) AND (:provider IS NULL OR t.provider=:provider) GROUP BY t.category_id,c.name ORDER BY amountFen DESC,t.category_id")
    List<CategoryRow> expenseCategories(long startMs, long endMs, String categoryId, String provider);
}
