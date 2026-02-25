package com.autosecretary.features.budget.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "budget_limits")
public class BudgetLimit {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public Long categoryId;
    public String yearMonth;
    public int limitCents;
    public int spentCents;
    public int rolloverCents;
    public String notes;
}
