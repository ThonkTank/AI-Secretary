package com.autosecretary.features.budget.data.legacy;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "categories")
@Deprecated
public class Category {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public String name;
    public String icon;
    public String color;
    public boolean isIncome;
    public boolean isBuiltIn;
    public int sortOrder;
    public boolean isActive;
}
