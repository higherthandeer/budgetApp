package com.example.budgetapp.data.entity;

import androidx.annotation.NonNull;
import androidx.room.*;

@Entity(tableName = "category", foreignKeys = {}, indices = {})
public class CategoryEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = "";
    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";
    @NonNull
    @ColumnInfo(name = "flow_scope")
    public String flowScope = "";
    @ColumnInfo(name = "sort_order")
    public int sortOrder;
    @ColumnInfo(name = "archived")
    public boolean archived;
    @NonNull
    @ColumnInfo(name = "icon_key", defaultValue = "'category'")
    public String iconKey = "category";
}
