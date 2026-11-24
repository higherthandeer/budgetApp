package com.example.budgetapp.data.dao;

import androidx.room.*;

import java.util.List;

import com.example.budgetapp.data.entity.*;

@Dao
public interface CategoryDao {
    @Query("SELECT r.id,r.version,r.category_id AS categoryId,c.flow_scope AS kind,r.provider,r.scope_filter AS scopeFilter,r.match_type AS matchType,r.merchant_pattern AS pattern,r.priority,r.enabled,c.archived AS categoryArchived FROM category_rule r JOIN category c ON c.id=r.category_id ORDER BY r.id")
    List<com.example.budgetapp.processing.CategoryMatcher.Rule> ruleRows();

    @Query("SELECT * FROM category_rule WHERE id=:id")
    CategoryRuleEntity findRule(String id);

    @Insert
    void insert(CategoryEntity category);

    @Update
    int update(CategoryEntity category);

    @Query("SELECT * FROM category WHERE id=:id")
    CategoryEntity find(String id);

    @Query("SELECT * FROM category ORDER BY CASE WHEN id IN ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002') THEN 1 ELSE 0 END,sort_order,id")
    List<CategoryEntity> all();

    @Insert
    void insertRule(CategoryRuleEntity rule);

    @Update
    int updateRule(CategoryRuleEntity rule);

    @Query("SELECT * FROM category_rule ORDER BY priority DESC,id")
    List<CategoryRuleEntity> rules();
}
