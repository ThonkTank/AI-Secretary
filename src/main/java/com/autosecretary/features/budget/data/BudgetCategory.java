package com.autosecretary.features.budget.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "budget_category")
public class BudgetCategory {
    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String name;

    /**
     * Allowed values: INCOME, EXPENSE.
     */
    @NonNull
    public String type = "EXPENSE";

    public boolean archived = false;

    public BudgetCategory(@NonNull String name, @NonNull String type) {
        this.name = name;
        this.type = type;
    }
}
