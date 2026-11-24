package com.example.budgetapp.data.entity;

import androidx.annotation.NonNull;
import androidx.room.*;

@Entity(tableName = "category_rule", foreignKeys = {@ForeignKey(entity = CategoryEntity.class, parentColumns = "id", childColumns = "category_id", onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT)}, indices = {@Index(value = {"category_id"}, unique = false)})
public class CategoryRuleEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = "";
    @ColumnInfo(name = "provider")
    public String provider;
    @ColumnInfo(name = "scope_filter")
    public String scopeFilter;
    @NonNull
    @ColumnInfo(name = "match_type")
    public String matchType = "";
    @NonNull
    @ColumnInfo(name = "merchant_pattern")
    public String merchantPattern = "";
    @NonNull
    @ColumnInfo(name = "category_id")
    public String categoryId = "";
    @ColumnInfo(name = "priority")
    public int priority;
    @ColumnInfo(name = "enabled")
    public boolean enabled;
    @ColumnInfo(name = "version")
    public long version;
}
